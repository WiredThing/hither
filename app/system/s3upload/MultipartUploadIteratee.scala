package system.s3upload

import fly.play.s3.{BucketFilePart, BucketFilePartUploadTicket}
import play.api.Logger
import play.api.libs.iteratee.{Cont, Input, Iteratee, Done}

import scala.concurrent.{Await, Future, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class MultipartUploadIteratee(uploader: MultipartUploader, uploadThreshold: Int)(implicit ec: ExecutionContext) {
  type IterateeType = Iteratee[Array[Byte], Unit]
  type StepFunction = (PartState) => (Input[Array[Byte]]) => IterateeType

  def step(state: PartState)(input: Input[Array[Byte]]): IterateeType = input match {
    case Input.El(bytes) => handleEl(step, state, bytes)
    case Input.Empty => handleEmpty(step, state)
    case Input.EOF => handleEOF(state)
  }

  def handleEmpty(step: StepFunction, state: PartState)(implicit ec: ExecutionContext): IterateeType =
    Cont[Array[Byte], Unit](i => step(state)(i))

  def handleEl(step: StepFunction, state: PartState, bytes: Array[Byte])(implicit ec: ExecutionContext): IterateeType = {
    val newState = state.addBytes(bytes)

    if (newState.accumulatedBytes.length >= uploadThreshold) {
      // Controversial. Blocks the iteratee until the chunk has been uploaded. If we don't do this
      // then the iteratee will happily consume all the incoming data and buffer it up in memory,
      // only discarding chunks when they've finished uploading. This could potentially lead
      // to out-of-memory errors. Blocking creates back-pressure to slow the data coming in, at
      // the cost of thread starvation if several uploads happen concurrently. Use a different thread
      // context, perhaps?
      val tickets = Await.result(uploadPart(newState), 10 minutes)
      Cont[Array[Byte], Unit](i => step(newState.nextPart(tickets))(i))
    } else {
      Cont[Array[Byte], Unit](i => step(newState)(i))
    }
  }

  def uploadPart(state: PartState)(implicit ec: ExecutionContext): Future[List[BucketFilePartUploadTicket]] = {
    Logger.info(s"Pushing part ${state.partNumber} with ${state.accumulatedBytes.length} bytes. ${state.totalSize} bytes uploaded so far")
    val f = uploader.uploadPart(BucketFilePart(state.partNumber.n, state.accumulatedBytes.toArray)).map(t => t +: state.uploadTickets)

    f.onFailure {
      case e =>
        Logger.error("Error during upload of chunk, aborting multipart upload", e)
        uploader.abortMultipartUpload
    }

    f
  }

  def handleEOF(state: PartState)(implicit ec: ExecutionContext): IterateeType = {
    val finalLength: DataLength = state.totalSize.add(state.accumulatedBytes.length)

    Logger.info(s"Got EOF as part number ${state.partNumber}. Total data size is $finalLength")

    Logger.info(s"Pushing final part ${state.partNumber} with ${state.accumulatedBytes.length} bytes")
    val finalState: Future[IterateeType] = uploadPart(state).flatMap { _ =>
      Logger.info(s"Completing upload with tickets for ${state.uploadTickets.size} parts")

      uploader.completeMultipartUpload(state.uploadTickets.reverse).map {
        case MultipartUploadSuccess => Logger.info(s"Multipart upload response completed successfully")
        case MultipartUploadError(status, error) =>
          Logger.info(s"Response to multipart upload completion was $status ($error). Aborting.")
          uploader.abortMultipartUpload
      }

    }.map(_ => Done(0, Input.EOF))

    finalState.onFailure {
      case e =>
        Logger.error("Error during upload of chunk, aborting multipart upload", e)
        uploader.abortMultipartUpload
    }

    // Here we want to wait until the upload is completed so that the docker push does not exit
    // before the data is actually on the registry.
    Await.result(finalState, 10 minutes)
  }
}
