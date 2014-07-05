package system.registry

import play.api.{Logger, Play}
import Play.current

import fly.play.s3.S3

import services.ContentEnumerator
import system.Configuration
import models.ImageId

import scala.concurrent.{ExecutionContext, Future}

trait S3Registry extends Registry {
  def bucketName: String

  override def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    val s3 = S3.fromConfig
    val pathName = s"${Configuration.registryRoot}/${imageId.id}.${resourceType.name}"

    val url: String = httpUrl(bucketName, s3.host, pathName)
    Logger.info(s"URL is $url")

    val ws = s3.awsWithSigner.url(url).sign("GET")

    Logger.info(s"Request is ${ws}")

    ws.getStream().map { result =>
      Logger.info(s"response is $result")
      result match {
        case (headers, e) if headers.status == 200 => Some(ContentEnumerator(e, resourceType.contentType, None))
        case (headers, _) if headers.status == 403 => throw new Exception("Unauthorized")
        case _ => None
      }
    }
  }


  def httpUrl(bucketName: String, host: String, path: String) = {

    val protocol = "http"
    protocol + "://" + bucketName + "." + host + "/" + path
  }
}
