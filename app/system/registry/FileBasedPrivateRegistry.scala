package system.registry

import java.io.File

import play.api.libs.iteratee.Iteratee

import services.ContentEnumerator
import models.ImageId

import scala.concurrent.{ExecutionContext, Future}
import system._
import scala.Some

final class FileBasedPrivateRegistry(val next: Registry) extends PrivateRegistry {
  def registryRoot: File = new File(system.Configuration.registryRoot)

  case class RegistryFile(file: File) extends FileLocalSource

  def localSourceFor(name: String): Option[LocalSource] = RegistryFile(new File(registryRoot, name)).existing

  override def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    val ce = localSourceFor(s"${imageId.id}.${resourceType.name}") match {
      case Some(source) => Some(ContentEnumerator(source.enumerator, resourceType.contentType, Some(source.length())))
      case None => None
    }

    Future.successful(ce)
  }

  override def putLayer(id: ImageId, body: Iteratee[Array[Byte], Unit]): Unit = ???

  override def putJson(id: ImageId, json: ImageJson): Future[Unit] = ???
}
