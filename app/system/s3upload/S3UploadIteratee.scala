package system.s3upload

import fly.play.s3._
import play.api.Logger
import play.api.libs.iteratee._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

object S3UploadIteratee {

  type StepFunction = (PartState) => (Input[Array[Byte]]) => Iteratee[Array[Byte], Unit]


  val FIVE_MEG: Int = 5 * 1024 * 1024

  def apply(bucket: Bucket, ticket: BucketFileUploadTicket)(implicit ctx: ExecutionContext): StepFunction = {
    val uploader = new BucketUploader(bucket, ticket)

    def step(state: PartState)(i: Input[Array[Byte]]): Iteratee[Array[Byte], Unit] = i match {
      case Input.EOF => handleEOF(uploader, state)
      case Input.El(bytes) => handleEl(uploader, step, state.addBytes(bytes))
      case Input.Empty => Cont[Array[Byte], Unit](i => step(state)(i))
    }

    step
  }

  def handleEl(uploader: MultipartUploader, step: StepFunction, state: PartState)
              (implicit ec: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    Logger.info(s"Got Input for part ${state.partNumber} with ${state.accumulatedBytes.length} bytes of data. Total so far is ${state.totalSize.add(state.accumulatedBytes.length)}")

    if (state.accumulatedBytes.length >= FIVE_MEG) {
      val f: Future[List[BucketFilePartUploadTicket]] = uploadPart(uploader, state)

      // Controversial. Blocks the iteratee until the chunk has been uploaded. If we don't do this
      // then the iteratee will happily consume all the incoming data and buffer it up in memory,
      // only discarding chunks when they've finished uploading. This could potentially lead
      // to out-of-memory errors. Blocking creates back-pressure to slow the data coming in, at
      // the cost of thread starvation if several uploads happen concurrently. Use a different thread
      // context, perhaps?
      Await.result(f, 10 minutes)

      Cont[Array[Byte], Unit](i => step(state.nextPart(f))(i))
    } else {
      Cont[Array[Byte], Unit](i => step(state)(i))
    }
  }

  def uploadPart(uploader: MultipartUploader, state: PartState)
                (implicit ec: ExecutionContext): Future[List[BucketFilePartUploadTicket]] = {
    val f = state.uploadTickets.flatMap { ts =>
      Logger.info(s"Pushing part ${state.partNumber} with ${state.accumulatedBytes.length} bytes. ${state.totalSize} bytes uploaded so far")
      uploader.uploadPart(BucketFilePart(state.partNumber.n, state.accumulatedBytes)).map(t => t +: ts)
    }

    f.onFailure {
      case e =>
        Logger.error("Error during upload of chunk, aborting multipart upload", e)
        uploader.abortMultipartUpload
    }
    f
  }

  def handleEOF(uploader: MultipartUploader, state: PartState)
               (implicit ec: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    val finalLength: DataLength = state.totalSize.add(state.accumulatedBytes.length)

    Logger.info(s"Got EOF as part number ${state.partNumber}. Total data size is $finalLength")

    val f = state.uploadTickets.flatMap { ts =>
      Logger.info(s"Pushing final part ${state.partNumber} with ${state.accumulatedBytes.length} bytes")
      val uploadTicket = uploader.uploadPart(BucketFilePart(state.partNumber.n, state.accumulatedBytes))

      uploadTicket.onFailure {
        case e =>
          Logger.error("Error during upload of chunk, aborting multipart upload", e)
          uploader.abortMultipartUpload
      }

      uploadTicket.map { t =>
        Logger.info(s"Completing upload with $t and tickets for ${ts.size + 1} parts")

        uploader.completeMultipartUpload((t +: ts).reverse).onComplete {
          case Success(MultipartUploadSuccess) => Logger.info(s"Multipart upload response completed successfully")
          case Success(MultipartUploadError(status, error)) =>
            Logger.info(s"Response to multipart upload completion was $status ($error). Aborting.")
            uploader.abortMultipartUpload
          case Failure(ex) =>
            Logger.info("Multipart upload failed. Aborting.", ex)
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
