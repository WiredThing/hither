package system.registry

import fly.play.s3.{BucketFile, S3}
import models.ImageId
import play.api.LoggerLike
import play.api.libs.iteratee._
import play.api.libs.ws.WSResponseHeaders
import services.ContentEnumerator
import system.Configuration
import system.registry.ResourceType.LayerType

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util._

trait S3Registry extends PrivateRegistry {
  def bucketName: String

  lazy val bucket = s3.getBucket(bucketName)

  def s3: S3

  def logger: LoggerLike

  implicit def app: play.api.Application

  /**
   * Each part of an S3 multipart upload, except for the last part, must be at least this
   * large or else S3 will reject the whole upload on completion.
   */
  val multipartUploadThreshold: Long = 5 * 1024 * 1024

  def protocol = if (Configuration.s3.useHttps) "https" else "http"

  def httpUrl(bucketName: String, host: String, path: String) = s"$protocol://$host/$bucketName/$path"

  def pathName(i: ImageId, r: ResourceType) = s"${Configuration.s3.registryRoot}/${i.id}.${r.name}"

  implicit class HeadersWrapper(headers: WSResponseHeaders) {
    def contentLength: Option[Long] = for {
      ls <- headers.headers.get("Content-Length")
      s <- ls.headOption
      l <- Try(s.toLong).toOption
    } yield l
  }

  override def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    val ws = s3.awsWithSigner.url(httpUrl(bucketName, s3.host, pathName(imageId, resourceType))).sign("GET")

    ws.withRequestTimeout((10 minutes).toMillis.toInt).getStream().map {
      case (headers, e) if headers.status == 200 => Some(ContentEnumerator(e, resourceType.contentType, headers.contentLength))
      case (headers, _) if headers.status == 403 => throw new Exception("Unauthorized")
      case _ => None
    }
  }

  override def layerHead(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[Long]] = {
    val bucketFile = BucketFile(pathName(imageId, LayerType), LayerType.contentType)

    Future {
      bucketFile.headers.flatMap { h =>
        h.get("Content-Length").flatMap(l => Try(l.toLong).toOption)
      }
    }
  }


  override def sinkFor(imageId: ImageId, resourceType: ResourceType, contentLength: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    val fileName = pathName(imageId, resourceType)

    contentLength match {
      case Some(l) if l < multipartUploadThreshold => logger.debug(s"Expecting $l bytes of data"); bucketUpload(fileName, resourceType)
      case Some(l) => logger.debug(s"Expecting $l bytes of data, uploading as multipart"); Iteratee.flatten(multipartUpload(fileName, resourceType))
      case None => logger.debug(s"Don't know how much data to expect"); Iteratee.flatten(multipartUpload(fileName, resourceType))
    }
  }

  protected def multipartUpload(fileName: String, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Iteratee[Array[Byte], Unit]] = {
    val bucketFile = BucketFile(fileName, resourceType.contentType)

    bucket.initiateMultipartUpload(bucketFile).map { ticket =>
      logger.debug(s"Multipart upload to ${bucketFile.name} started with ticket ${ticket}")

      val step = S3UploadIteratee(bucket, ticket)
      Cont[Array[Byte], Unit](i => step(1, 0, Array(), Future(List()))(i))
    }.recover {
      case t => logger.error("Got error trying to initiate upload", t); throw t
    }
  }

  def bucketUpload(fileName: String, resourceType: ResourceType)(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    Iteratee.consume[Array[Byte]]().map { bytes =>
      logger.debug(s"Consumed ${bytes.length} bytes of data")
      logger.debug(s"Sending to bucketFile with name $fileName")
      val bucketFile = BucketFile(fileName, resourceType.contentType, bytes)

      bucket.add(bucketFile).onComplete {
        case Success(_) => logger.debug(s"Successfully uploaded $fileName")
        case Failure(t) => logger.debug(s"Upload of $fileName failed with ${t.getMessage}")
      }
    }
  }
}
