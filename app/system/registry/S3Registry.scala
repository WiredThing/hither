package system.registry

import fly.play.s3.{BucketFilePartUploadTicket, BucketFilePart, BucketFile, S3}
import models.ImageId
import play.api.{libs, Logger}
import play.api.libs.iteratee._
import play.api.libs.ws
import play.api.libs.ws.StreamedBody
import services.ContentEnumerator
import system.Configuration

import scala.concurrent.{ExecutionContext, Future}

trait S3Registry extends Registry {
  def bucketName: String

  def s3: S3

  implicit def app: play.api.Application

  override def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    val pathName = s"${Configuration.registryRoot}/${imageId.id}.${resourceType.name}"

    val url: String = httpUrl(bucketName, s3.host, pathName)

    val ws = s3.awsWithSigner.url(url).sign("GET")

    ws.getStream().map { result =>
      Logger.info(s"response is $result")
      result match {
        case (headers, e) if headers.status == 200 => Some(ContentEnumerator(e, resourceType.contentType, None))
        case (headers, _) if headers.status == 403 => throw new Exception("Unauthorized")
        case _ => None
      }
    }
  }

  override def sinkFor(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    import scala.concurrent.Await
    import scala.concurrent.duration._

    val bucket = s3.getBucket(bucketName)
    val fileName = s"${Configuration.registryRoot}/${imageId.id}.${resourceType.name}"
    val bucketFile = BucketFile(fileName, resourceType.contentType)

    val fit = bucket.initiateMultipartUpload(bucketFile).map { ticket =>
      def step(partNumber: Int, totalSize:Int, currentChunk: Array[Byte], uploadTickets: List[BucketFilePartUploadTicket])(i: Input[Array[Byte]]): Iteratee[Array[Byte], Unit] = i match {
        case Input.EOF =>
          Logger.info(s"Pushing part $partNumber with ${currentChunk.length} bytes")
          val uploadTicket = Await.result(bucket.uploadPart(ticket, BucketFilePart(partNumber, currentChunk)), 10 minutes)
          Logger.info(s"Completing upload with $ticket and tickets for ${uploadTickets.size+1} parts")
          Await.result(bucket.completeMultipartUpload(ticket, (uploadTicket +: uploadTickets).reverse), 10 minutes)
          Done(0, Input.EOF)

        case Input.El(bytes) =>
          val chunk = currentChunk ++ bytes
          if (chunk.length >= 128 * 1024) {
            Logger.info(s"Pushing part $partNumber with ${chunk.length} bytes. $totalSize bytes uploaded so far")
            val uploadTicket = Await.result(bucket.uploadPart(ticket, BucketFilePart(partNumber, chunk)), 10 minutes)
            Cont[Array[Byte], Unit](i => step(partNumber + 1, totalSize + chunk.length, Array(), uploadTicket +: uploadTickets)(i))
          } else {
            Cont[Array[Byte], Unit](i => step(partNumber, totalSize, chunk, uploadTickets)(i))
          }

        case Input.Empty => Cont[Array[Byte], Unit](i => step(partNumber, totalSize, currentChunk, uploadTickets)(i))
      }

      Cont[Array[Byte], Unit](i => step(1, 0, Array(), List())(i))
    }

    Iteratee.flatten(fit)
  }

  def httpUrl(bucketName: String, host: String, path: String) = {
    val protocol = "http"
    protocol + "://" + bucketName + "." + host + "/" + path
  }
}
