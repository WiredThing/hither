package system.s3upload

import fly.play.s3.{Bucket, BucketFilePart, BucketFilePartUploadTicket, BucketFileUploadTicket}

import scala.concurrent.{ExecutionContext, Future}

sealed trait MultipartUploadResult
case object MultipartUploadSuccess extends MultipartUploadResult
case class MultipartUploadError(status:Int, error:String) extends MultipartUploadResult

trait MultipartUploader {
  def uploadPart(part: BucketFilePart)(implicit ec: ExecutionContext): Future[BucketFilePartUploadTicket]

  def abortMultipartUpload(implicit ec: ExecutionContext): Future[Unit]

  def completeMultipartUpload(partUploadTickets: Seq[BucketFilePartUploadTicket])(implicit ec: ExecutionContext): Future[MultipartUploadResult]
}

case class BucketUploader(bucket: Bucket, ticket: BucketFileUploadTicket) extends MultipartUploader {
  override def uploadPart(part: BucketFilePart)(implicit ec: ExecutionContext): Future[BucketFilePartUploadTicket] =
    bucket.uploadPart(ticket, part)

  override def completeMultipartUpload(partUploadTickets: Seq[BucketFilePartUploadTicket])(implicit ec: ExecutionContext): Future[MultipartUploadResult] =
    bucket.s3.completeMultipartUpload(bucket.name, ticket, partUploadTickets).map {r =>
      r.status match {
        case 200 => MultipartUploadSuccess
        case _ => MultipartUploadError(r.status, r.body)
      }
    }

  override def abortMultipartUpload(implicit ec: ExecutionContext): Future[Unit] = bucket.abortMultipartUpload(ticket)
}