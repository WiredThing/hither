package system

import java.io.File

import models.ImageId
import play.api.libs.iteratee.Iteratee
import services.ContentEnumerator
import system.RegistryType._

import scala.concurrent.{ExecutionContext, Future}

class PrivateRegistry extends Registry {

  override def layer(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    findResource(imageId, LayerType)
  }

  override def json(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    findResource(imageId, JsonType)
  }

  override def ancestry(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    findResource(imageId, AncestryType) flatMap {
      case Some(ce) => Future(Some(ce))
      case None => if (buildRegistryPath(s"${imageId.id}.${LayerType.name}").exists()) {
        constructAncestry(imageId)
      } else {
        Future(None)
      }
    }
  }

  protected def constructAncestry(imageId: ImageId): Future[Option[ContentEnumerator]] = ???

  protected def findResource(imageId: ImageId, registryType: RegistryType): Future[Option[ContentEnumerator]] = {
    val ce = buildRegistryPath(s"${imageId.id}.${registryType.name}").existing match {
      case Some(source) => Some(ContentEnumerator(source.enumerator, registryType.contentType, Some(source.length())))
      case None => None
    }

    Future.successful(ce)
  }

  override def putLayer(id: ImageId, body: Iteratee[Array[Byte], Unit]): Unit = ???

  override def putJson(id: ImageId, json: ImageJson): Future[Unit] = ???

  def registryRoot: File = new File(system.Configuration.registryRoot)

  case class RegistryFile(file: File) extends FileLocalSource

  def buildRegistryPath(name: String): RegistryFile = RegistryFile(new File(registryRoot, name))
}
