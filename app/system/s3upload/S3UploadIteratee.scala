package system.s3upload

import fly.play.s3.{BucketFileUploadTicket, Bucket}
import play.api.libs.iteratee._

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object S3UploadIteratee {
  type IterateeType = Iteratee[Array[Byte], Unit]
  type StepFunction = (PartState) => (Input[Array[Byte]]) => IterateeType

  val FIVE_MEG: Int = 5 * 1024 * 1024

  def apply(bucket: Bucket, ticket: BucketFileUploadTicket)(implicit ctx: ExecutionContext): StepFunction = {
    new MultipartUploadIteratee(new BucketUploader(bucket, ticket), FIVE_MEG).step
  }
}


