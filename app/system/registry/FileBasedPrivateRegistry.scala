package system.registry

import java.io.{File, OutputStream}

import scala.concurrent.{ExecutionContext, Future}

import play.api.Logger
import play.api.libs.iteratee.Iteratee

import models.ImageId
import services.ContentEnumerator
import system._


trait FileBasedPrivateRegistry extends PrivateRegistry {
  def registryRoot: File = new File(system.Configuration.file.registryRoot)

  case class RegistryFile(file: File) extends FileLocalSource

  def init: Unit = {
    Logger.info(s"Creating $registryRoot")
    registryRoot.mkdirs()
  }

  def fileName(imageId: ImageId, resourceType: ResourceType): String =
    s"${imageId.id}.${resourceType.name}"

  def outputFor(imageId: ImageId, resourceType: ResourceType): LocalSource =
    RegistryFile(new File(registryRoot, fileName(imageId, resourceType)))

  def localSourceFor(imageId: ImageId, resourceType: ResourceType): Option[LocalSource] = localSourceFor(s"${imageId.id}.${resourceType.name}")

  private def localSourceFor(name: String): Option[LocalSource] = RegistryFile(new File(registryRoot, name)).existing

  override def sinkFor(id: ImageId, resourceType: ResourceType, contentLength: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    val os = outputStreamFor(id, resourceType)
    Iteratee.fold[Array[Byte], OutputStream](os) { (os, data) =>
      os.write(data)
      os
    }.map { os =>
      os.close()
      Right(Unit)
    }
  }

  def outputStreamFor(id: ImageId, resourceType: ResourceType): OutputStream =
    outputFor(id, resourceType).outputStream

  override def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    val ce = localSourceFor(imageId, resourceType) match {
      case Some(source) => Some(ContentEnumerator(source.enumerator, resourceType.contentType, Some(source.length())))
      case None => None
    }

    Future.successful(ce)
  }
}
