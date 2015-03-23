package system.s3upload

import fly.play.s3.{BucketFilePartUploadTicket, BucketFilePart}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * A dummy implementation that will throw an exception if any of its methods are called. Useful to test that no
 * upload operations happened during the test
 */
class UnusedUploader extends MultipartUploader {
  override def uploadPart(part: BucketFilePart)(implicit ec: ExecutionContext): Future[BucketFilePartUploadTicket] = ???

  override def completeMultipartUpload(partUploadTickets: Seq[BucketFilePartUploadTicket])(implicit ec: ExecutionContext): Future[MultipartUploadResult] = ???

  override def abortMultipartUpload(implicit ec: ExecutionContext): Future[Unit] = ???
}
