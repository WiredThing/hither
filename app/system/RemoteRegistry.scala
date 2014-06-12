package system

import models.{ImageId, Image}
import play.api.libs.iteratee.{Enumerator, Iteratee}

import scala.concurrent.Future

class RemoteRegistry extends Registry{
  override def layer(id: ImageId): Future[Enumerator[Array[Byte]]] = ???

  override def ancestry(id: ImageId): Future[Ancestry] = ???

  override def putLayer(id: Image, body: Iteratee[Array[Byte], Unit]): Unit = ???

  override def json(id: Image): Future[ImageJson] = ???

  override def putJson(id: Image, json: ImageJson): Future[Unit] = ???
}
