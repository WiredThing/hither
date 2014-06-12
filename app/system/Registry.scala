package system

import models.{Image, ImageId}
import play.api.libs.iteratee.{Iteratee, Enumerator}
import play.api.libs.json.JsValue
import services.ContentEnumerator

import scala.concurrent.{ExecutionContext, Future}

trait Registry {
  type Ancestry = List[ImageId]

  type ImageJson = JsValue

  def layer(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]

  def ancestry(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]

  def json(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]


  def putLayer(id: ImageId, body: Iteratee[Array[Byte], Unit])

  def putJson(id: ImageId, json: ImageJson): Future[Unit]
}
