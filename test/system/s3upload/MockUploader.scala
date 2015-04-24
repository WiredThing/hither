package system.s3upload

import fly.play.s3.{BucketFilePartUploadTicket, BucketFilePart}

import scala.concurrent.{Future, ExecutionContext}

class MockUploader extends MultipartUploader {
  var uploadCount = 0

  override def uploadPart(part: BucketFilePart)(implicit ec: ExecutionContext): Future[BucketFilePartUploadTicket] = {
    uploadCount = uploadCount + 1
    Future(BucketFilePartUploadTicket(1, ""))
  }

  var completeCount = 0

  override def completeMultipartUpload(partUploadTickets: Seq[BucketFilePartUploadTicket])(implicit ec: ExecutionContext): Future[MultipartUploadResult] = {
    completeCount = completeCount + 1
    Future(MultipartUploadSuccess)
  }

  var abortCount = 0

  override def abortMultipartUpload(implicit ec: ExecutionContext): Future[Unit] = {
    abortCount = abortCount + 1
    Future(Unit)
  }
}
