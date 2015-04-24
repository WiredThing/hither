package system.registry

import models.ImageId
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import services.ContentEnumerator

import scala.concurrent.{ExecutionContext, Future}

case class ResourceType(name: String, contentType: String)

object ResourceType {
  val AncestryType = ResourceType("ancestry", "application/json")
  val JsonType = ResourceType("json", "application/json")
  val LayerType = ResourceType("layer", "binary/octet-stream")
  val ChecksumType = ResourceType("checksum", "application/json")
}

trait Registry {
  type Ancestry = List[ImageId]

  type ImageJson = JsValue

  import ResourceType._

  def resourceExists(imageId:ImageId, resourceType:ResourceType)(implicit ctx: ExecutionContext): Future[Boolean]

  def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]

  def layerHead(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[Long]]

  def layer(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] =
    findResource(imageId, LayerType)

  def json(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] =
    resourceExists(imageId, LayerType).flatMap {
      case true => findResource(imageId, JsonType)
      case false => Future(None)
    }

  def ancestry(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] =
    findResource(imageId, AncestryType)

  def sinkFor(id: ImageId, resourceType: ResourceType, contentLength: Option[Long] = None)(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit]

  def init(): Unit = {}
}
