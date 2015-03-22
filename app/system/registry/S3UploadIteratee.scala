package system.registry

import scala.language.postfixOps
import scala.util.{Failure, Success}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import play.api.Logger
import play.api.libs.iteratee._
import fly.play.s3._


object S3UploadIteratee {

  type StepFunction = (PartNumber, DataLength, Array[Byte], Future[List[BucketFilePartUploadTicket]]) => (Input[Array[Byte]]) => Iteratee[Array[Byte], Unit]

  case class PartNumber(n: Int) extends AnyVal {
    def inc: PartNumber = PartNumber(n + 1)
  }

  object PartNumber {
    val one = PartNumber(1)
  }

  case class DataLength(l: Int) extends AnyVal {
    def add(a: Int) = DataLength(l + a)
  }

  object DataLength {
    val zero = DataLength(0)
  }

  def apply(bucket: Bucket, ticket: BucketFileUploadTicket)(implicit ctx: ExecutionContext): StepFunction = {
    def step(partNumber: PartNumber, totalSize: DataLength, accumulatedChunks: Array[Byte], uploadTickets: Future[List[BucketFilePartUploadTicket]])(i: Input[Array[Byte]]): Iteratee[Array[Byte], Unit] = i match {
      case Input.EOF => handleEOF(bucket, ticket, partNumber, totalSize, accumulatedChunks, uploadTickets)
      case Input.El(bytes) => handleEl(bucket, ticket, step, partNumber, totalSize, accumulatedChunks, uploadTickets, bytes)
      case Input.Empty => Cont[Array[Byte], Unit](i => step(partNumber, totalSize, accumulatedChunks, uploadTickets)(i))
    }

    step
  }

  def handleEl(bucket: Bucket,
               ticket: BucketFileUploadTicket,
               step: StepFunction,
               partNumber: PartNumber,
               totalSize: DataLength,
               accumulatedBytes: Array[Byte],
               uploadTickets: Future[List[BucketFilePartUploadTicket]],
               bytes: Array[Byte])(implicit ec: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    val chunk = accumulatedBytes ++ bytes
    if (chunk.length >= 5 * 1024 * 1024) {
      Logger.info(s"Got Input for part $partNumber with ${chunk.length} bytes of data. Total so far is ${totalSize.add(chunk.length)}")

      val f = uploadTickets.flatMap { ts =>
        Logger.info(s"Pushing part $partNumber with ${chunk.length} bytes. $totalSize bytes uploaded so far")
        bucket.uploadPart(ticket, BucketFilePart(partNumber.n, chunk)).map { t =>
          t +: ts
        }
      }

      f.onFailure {
        case e =>
          Logger.error("Error during upload of chunk, aborting multipart upload", e)
          bucket.abortMultipartUpload(ticket)
      }

      // Controversial. Blocks the iteratee until the chunk has been uploaded. If we don't do this
      // then the iteratee will happily consume all the incoming data and buffer it up in memory,
      // only discarding chunks when they've finished uploading. This could potentially lead
      // to out-of-memory errors. Blocking creates back-pressure to slow the data coming in, at
      // the cost of thread starvation if several uploads happen concurrently. Use a different thread
      // context, perhaps?
      Await.result(f, 10 minutes)

      Cont[Array[Byte], Unit](i => step(partNumber.inc, totalSize.add(chunk.length), Array(), f)(i))
    } else {
      Cont[Array[Byte], Unit](i => step(partNumber, totalSize, chunk, uploadTickets)(i))
    }
  }

  def handleEOF(bucket: Bucket,
                ticket: BucketFileUploadTicket,
                partNumber: PartNumber,
                totalSize: DataLength,
                accumulatedChunks: Array[Byte],
                uploadTickets: Future[List[BucketFilePartUploadTicket]])(implicit ec: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    val finalLength: DataLength = totalSize.add(accumulatedChunks.length)
    Logger.info(s"Got EOF as part number $partNumber. Total data size is $finalLength")
    val f = uploadTickets.flatMap { ts =>
      Logger.info(s"Pushing final part $partNumber with ${accumulatedChunks.length} bytes")
      val uploadTicket = bucket.uploadPart(ticket, BucketFilePart(partNumber.n, accumulatedChunks))

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

    // Here we want to wait until the upload is completed so that the docker push does not exit
    // before the data is actually on the registry.
    Await.result(f, 10 minutes)
    Done(0, Input.EOF)
  }
}
