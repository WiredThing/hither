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

  def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]

  def layer(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = findResource(imageId, LayerType)

  def json(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = findResource(imageId, JsonType)

  def ancestry(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = findResource(imageId, AncestryType)

  def putLayer(id: ImageId, body: Iteratee[Array[Byte], Unit]) = ???

  def putJson(id: ImageId, json: ImageJson): Future[Unit] = ???
}
