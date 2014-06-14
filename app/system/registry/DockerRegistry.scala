package system.registry

import models.ImageId
import scala.concurrent.{Future, ExecutionContext}
import services.{NotFoundException, ContentEnumerator}
import play.api.libs.ws.WS
import scala.util.Try
import play.api.Play.current
import system.{ResourceType, Registry}

trait DockerRegistry extends Registry {
  def registryHostName: String

  override def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    val url: String = s"http://$registryHostName/v1/images/${imageId.id}/${resourceType.name}"
    WS.url(url).getStream.map { response =>
      response match {
        case (headers, enumerator) =>
          headers.status match {
            case 200 => for {
              lengths <- headers.headers.get("Content-Length")
              length <- lengths.headOption
            } yield ContentEnumerator(enumerator, resourceType.contentType, Try(length.toLong).toOption)

            case 404 => throw NotFoundException(url)

            case status => throw new Exception(s"Unexpected response $status from $url")
          }
      }
    }
  }
}
