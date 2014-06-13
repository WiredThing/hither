package system

import models.ImageId
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import services.ContentEnumerator

import scala.concurrent.{ExecutionContext, Future}

trait AncestryFinder {
  def ancestry(imageId: ImageId)(implicit fallback: AncestryFinder, ctx: ExecutionContext): Future[Option[ContentEnumerator]]
}

trait Registry extends AncestryFinder {
  type Ancestry = List[ImageId]

  type ImageJson = JsValue

  def layer(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]

  def json(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]


  def putLayer(id: ImageId, body: Iteratee[Array[Byte], Unit])

  def putJson(id: ImageId, json: ImageJson): Future[Unit]
}
