package system

import models.{ImageId, Image}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import services.ContentEnumerator

import scala.concurrent.{ExecutionContext, Future}

class RemoteRegistry extends Registry{
  override def layer(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = ???

  override def ancestry(imageId: ImageId)(implicit fallback: AncestryFinder, ctx: ExecutionContext): Future[Option[ContentEnumerator]] = ???

  override def putLayer(id: ImageId, body: Iteratee[Array[Byte], Unit]): Unit = ???

  override def json(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = ???

  override def putJson(id: ImageId, json: ImageJson): Future[Unit] = ???
}
