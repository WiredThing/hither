package system.registry

import fly.play.s3.{BucketFile, S3}
import models.ImageId
import play.api.Logger
import play.api.libs.iteratee._
import services.ContentEnumerator
import system.Configuration

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util._

trait S3Registry extends PrivateRegistry {
  def bucketName: String

  def s3: S3

  implicit def app: play.api.Application

  val multipartUploadThreshold: Long = 5 * 1024 * 1024

  override def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {

    val pathName = s"${Configuration.s3.registryRoot}/${imageId.id}.${resourceType.name}"

    val url: String = httpUrl(bucketName, s3.host, pathName)

    val ws = s3.awsWithSigner.url(url).sign("GET")

    ws.withRequestTimeout((10 minutes).toMillis.toInt).getStream().map { result =>
      result match {
        case (headers, e) if headers.status == 200 =>
          val contentLength = for {
            ls <- headers.headers.get("Content-Length")
            s <- ls.headOption
            l <- Try(s.toLong).toOption
          } yield l
          Some(ContentEnumerator(e, resourceType.contentType, contentLength))
        case (headers, _) if headers.status == 403 => throw new Exception("Unauthorized")
        case _ => None
      }
    }
  }


  override def layerHead(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[Long]] = {
    val bucket = s3.getBucket(bucketName)
    val fileName = s"${Configuration.s3.registryRoot}/${imageId.id}.layer"
    val bucketFile = BucketFile(fileName, ResourceType.LayerType.contentType)

    Future {
      bucketFile.headers.flatMap { h =>
        h.get("Content-Length").flatMap(l => Try(l.toLong).toOption)
      }
    }
  }

  lazy val bucket = s3.getBucket(bucketName)

  override def sinkFor(imageId: ImageId, resourceType: ResourceType, contentLength: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    val fileName = s"${Configuration.s3.registryRoot}/${imageId.id}.${resourceType.name}"

    contentLength match {
      case Some(l) if l < multipartUploadThreshold => Logger.info(s"Expecting $l bytes of data"); bucketUpload(fileName, resourceType)
      case Some(l) => Logger.info(s"Expecting $l bytes of data, uploading as multipart"); Iteratee.flatten(multipartUpload(fileName, resourceType))
      case None => Logger.info(s"Don't know how much data to expect"); Iteratee.flatten(multipartUpload(fileName, resourceType))
    }
  }

  def multipartUpload(fileName: String, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Iteratee[Array[Byte], Unit]] = {
    val bucketFile = BucketFile(fileName, resourceType.contentType)

    bucket.initiateMultipartUpload(bucketFile).map { ticket =>
      Logger.info(s"Multipart upload to ${bucketFile.name} started with ticket ${ticket}")

      val step = S3UploadIteratee(bucket, ticket)

      Cont[Array[Byte], Unit](i => step(1, 0, Array(), Future(List()))(i))
    }.recover {
      case t => Logger.error("Got error trying to initiate upload", t); throw t
    }
  }

  def bucketUpload(fileName: String, resourceType: ResourceType)(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    Iteratee.consume[Array[Byte]]().map { bytes =>
      Logger.info(s"Consumed ${bytes.length} bytes of data")
      Logger.info(s"Sending to bucketFile with name $fileName")
      val bucketFile = BucketFile(fileName, resourceType.contentType, bytes)

      bucket.add(bucketFile).onComplete {
        case Success(_) => Logger.debug(s"Successfully uploaded $fileName")
        case Failure(t) => Logger.debug(s"Upload of $fileName failed with ${t.getMessage}")
      }
    }
  }

  def httpUrl(bucketName: String, host: String, path: String) = {
    val protocol = "http"
    protocol + "://" + bucketName + "." + host + "/" + path
  }
}
