package system.registry

import scala.language.postfixOps
import scala.util.{Failure, Success}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import play.api.Logger
import play.api.libs.iteratee._
import fly.play.s3._


object S3UploadIteratee {

  def apply(bucket: Bucket, ticket: BucketFileUploadTicket)(implicit ctx: ExecutionContext):
  (Int, Int, Array[Byte], Future[List[BucketFilePartUploadTicket]]) => (Input[Array[Byte]]) => Iteratee[Array[Byte], Unit] = {

    def step(partNumber: Int, totalSize: Int, accumulatedChunks: Array[Byte], uploadTickets: Future[List[BucketFilePartUploadTicket]])(i: Input[Array[Byte]]): Iteratee[Array[Byte], Unit] = i match {
      case Input.EOF => {
        Logger.info(s"Got EOF as part number $partNumber. Total data size is ${totalSize + accumulatedChunks.length}")
        val f = uploadTickets.flatMap { ts =>
          Logger.info(s"Pushing final part $partNumber with ${accumulatedChunks.length} bytes")
          val uploadTicket = bucket.uploadPart(ticket, BucketFilePart(partNumber, accumulatedChunks))

          uploadTicket.onFailure {
            case e =>
              Logger.error("Error during upload of chunk, aborting multipart upload", e)
              bucket.abortMultipartUpload(ticket)
          }

          uploadTicket.map { t =>
            Logger.info(s"Completing upload with $t and tickets for ${ts.size + 1} parts")
            bucket.s3.completeMultipartUpload(bucket.name, ticket, (t +: ts).reverse).onComplete {
              case Success(response) => response.status match {
                case 200 => Logger.info(s"Multipart upload response was ${response.status}")
                case x => Logger.info(s"Response to multipart upload completion was $x (${response.body})")
              }
              case Failure(ex) =>
                Logger.info("Multipart upload failed", ex)
                bucket.abortMultipartUpload(ticket)
            }
          }
        }

        // Contraversial. Blocks the iteratee until the chunk has been uploaded. If we don't do this
        // then the iteratee will happliy consume all the incoming data and buffer it up in memory,
        // only discarding chunk when they've finished uploading. This could potentially lead
        // to out-of-memory errors. Blocking creates back-pressure to slow the data coming in, at
        // the cost of thread starvation if several uploads happen concurrently. Use a different thread
        // context, perhaps?
        Await.result(f, 10 minutes)
        Done(0, Input.EOF)
      }

      case Input.El(bytes) =>
        val chunk = accumulatedChunks ++ bytes
        if (chunk.length >= 5 * 1024 * 1024) {
          Logger.info(s"Got Input for part $partNumber with ${chunk.length} bytes of data. Total so far is ${totalSize + chunk.length}")

          val f = uploadTickets.flatMap { ts =>
            Logger.info(s"Pushing part $partNumber with ${chunk.length} bytes. $totalSize bytes uploaded so far")
            bucket.uploadPart(ticket, BucketFilePart(partNumber, chunk)).map { t =>
              t +: ts
            }
          }

          f.onFailure {
            case e =>
              Logger.error("Error during upload of chunk, aborting multipart upload", e)
              bucket.abortMultipartUpload(ticket)
          }

          Await.result(f, 10 minutes)

          Cont[Array[Byte], Unit](i => step(partNumber + 1, totalSize + chunk.length, Array(), f)(i))
        } else {
          Cont[Array[Byte], Unit](i => step(partNumber, totalSize, chunk, uploadTickets)(i))
        }

      case Input.Empty => Cont[Array[Byte], Unit](i => step(partNumber, totalSize, accumulatedChunks, uploadTickets)(i))
    }

    step
  }

}
