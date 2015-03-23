package system.registry

import play.api.libs.ws.WSResponse

import scala.language.postfixOps
import scala.util.{Failure, Success}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import play.api.Logger
import play.api.libs.iteratee._
import fly.play.s3._

trait MultipartUploader {
  def uploadPart(part: BucketFilePart)(implicit ec: ExecutionContext): Future[BucketFilePartUploadTicket]

  def abortMultipartUpload(implicit ec: ExecutionContext): Future[Unit]

  def completeMultipartUpload(partUploadTickets: Seq[BucketFilePartUploadTicket])(implicit ec: ExecutionContext): Future[WSResponse]
}

case class BucketUploader(bucket: Bucket, ticket: BucketFileUploadTicket) extends MultipartUploader {
  override def uploadPart(part: BucketFilePart)(implicit ec: ExecutionContext): Future[BucketFilePartUploadTicket] =
    bucket.uploadPart(ticket, part)

  override def completeMultipartUpload(partUploadTickets: Seq[BucketFilePartUploadTicket])(implicit ec: ExecutionContext): Future[WSResponse] =
    bucket.s3.completeMultipartUpload(bucket.name, ticket, partUploadTickets)

  override def abortMultipartUpload(implicit ec: ExecutionContext): Future[Unit] = bucket.abortMultipartUpload(ticket)
}

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

  val FIVE_MEG: Int = 5 * 1024 * 1024

  def apply(bucket: Bucket, ticket: BucketFileUploadTicket)(implicit ctx: ExecutionContext): StepFunction = {
    val uploader = new BucketUploader(bucket, ticket)
    
    def step(partNumber: PartNumber, totalSize: DataLength, accumulatedBytes: Array[Byte], uploadTickets: Future[List[BucketFilePartUploadTicket]])(i: Input[Array[Byte]]): Iteratee[Array[Byte], Unit] = i match {
      case Input.EOF => handleEOF(uploader, partNumber, totalSize, accumulatedBytes, uploadTickets)
      case Input.El(bytes) => handleEl(uploader, step, partNumber, totalSize, accumulatedBytes ++ bytes, uploadTickets)
      case Input.Empty => Cont[Array[Byte], Unit](i => step(partNumber, totalSize, accumulatedBytes, uploadTickets)(i))
    }

    step
  }

  def handleEl(uploader: MultipartUploader,
               step: StepFunction,
               partNumber: PartNumber,
               totalSize: DataLength,
               accumulatedBytes: Array[Byte],
               uploadTickets: Future[List[BucketFilePartUploadTicket]])
              (implicit ec: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    Logger.info(s"Got Input for part $partNumber with ${accumulatedBytes.length} bytes of data. Total so far is ${totalSize.add(accumulatedBytes.length)}")

    if (accumulatedBytes.length >= FIVE_MEG) {
      val f: Future[List[BucketFilePartUploadTicket]] = uploadPart(uploader, partNumber, totalSize, accumulatedBytes, uploadTickets)

      // Controversial. Blocks the iteratee until the chunk has been uploaded. If we don't do this
      // then the iteratee will happily consume all the incoming data and buffer it up in memory,
      // only discarding chunks when they've finished uploading. This could potentially lead
      // to out-of-memory errors. Blocking creates back-pressure to slow the data coming in, at
      // the cost of thread starvation if several uploads happen concurrently. Use a different thread
      // context, perhaps?
      Await.result(f, 10 minutes)

      Cont[Array[Byte], Unit](i => step(partNumber.inc, totalSize.add(accumulatedBytes.length), Array(), f)(i))
    } else {
      Cont[Array[Byte], Unit](i => step(partNumber, totalSize, accumulatedBytes, uploadTickets)(i))
    }
  }

  def uploadPart(uploader: MultipartUploader,
                 partNumber: PartNumber,
                 totalSize: DataLength,
                 accumulatedBytes: Array[Byte],
                 uploadTickets: Future[List[BucketFilePartUploadTicket]])
                (implicit ec: ExecutionContext): Future[List[BucketFilePartUploadTicket]] = {
    val f = uploadTickets.flatMap { ts =>
      Logger.info(s"Pushing part $partNumber with ${accumulatedBytes.length} bytes. $totalSize bytes uploaded so far")
      uploader.uploadPart(BucketFilePart(partNumber.n, accumulatedBytes)).map(t => t +: ts)
    }

    f.onFailure {
      case e =>
        Logger.error("Error during upload of chunk, aborting multipart upload", e)
        uploader.abortMultipartUpload
    }
    f
  }

  def handleEOF(uploader: MultipartUploader,
                partNumber: PartNumber,
                totalSize: DataLength,
                accumulatedBytes: Array[Byte],
                uploadTickets: Future[List[BucketFilePartUploadTicket]])
               (implicit ec: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    val finalLength: DataLength = totalSize.add(accumulatedBytes.length)

    Logger.info(s"Got EOF as part number $partNumber. Total data size is $finalLength")

    val f = uploadTickets.flatMap { ts =>
      Logger.info(s"Pushing final part $partNumber with ${accumulatedBytes.length} bytes")
      val uploadTicket = uploader.uploadPart(BucketFilePart(partNumber.n, accumulatedBytes))

      uploadTicket.onFailure {
        case e =>
          Logger.error("Error during upload of chunk, aborting multipart upload", e)
          uploader.abortMultipartUpload
      }

      uploadTicket.map { t =>
        Logger.info(s"Completing upload with $t and tickets for ${ts.size + 1} parts")

        uploader.completeMultipartUpload((t +: ts).reverse).onComplete {
          case Success(response) => response.status match {
            case 200 => Logger.info(s"Multipart upload response was ${response.status}")
            case x => Logger.info(s"Response to multipart upload completion was $x (${response.body})")
          }
          case Failure(ex) =>
            Logger.info("Multipart upload failed", ex)
            uploader.abortMultipartUpload
        }
      }
    }

    // Here we want to wait until the upload is completed so that the docker push does not exit
    // before the data is actually on the registry.
    Await.result(f, 10 minutes)

    Done(0, Input.EOF)
  }
}
