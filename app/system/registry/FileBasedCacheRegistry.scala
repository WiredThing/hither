package system.registry

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.iteratee.Enumerator

import services.ContentEnumerator
import system.{LocalSource, FileLocalSource}
import models.ImageId

trait FileBasedCacheRegistry extends Registry {
  def next: Registry

  def cacheRoot: File = new File(system.Configuration.cacheRoot)

  case class RegistryFile(file: File) extends FileLocalSource

  import ResourceType._

  protected def localSourceFor(imageId: ImageId, resourceType: ResourceType): Option[LocalSource] = {
    RegistryFile(new File(cacheRoot, s"${imageId.id}.${resourceType.name}")).existing
  }

  def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    val ce = localSourceFor(imageId, resourceType) match {
      case Some(source) => Some(ContentEnumerator(source.enumerator, resourceType.contentType, Some(source.length())))
      case None => None
    }

    Future.successful(ce)
  }

  protected def teeResource(imageId: ImageId, resourceType: ResourceType, ce: ContentEnumerator)(implicit ctx: ExecutionContext): ContentEnumerator = {
    val os = RegistryFile(new File(cacheRoot, s"${imageId.id}.${resourceType.name}")).outputStream

    val fileWriter: Enumerator[Array[Byte]] = ce.content.map { bytes =>
      os.write(bytes)
      bytes
    }

    fileWriter.onDoneEnumerating(os.close())
    ce.copy(content = fileWriter)
  }

  protected def findOrCacheResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext) =
    findResource(imageId, resourceType).flatMap {
      case None => next.layer(imageId).map { oce =>
        oce.map(ce => teeResource(imageId, resourceType, ce))
      }
      case some => Future(some)
    }

  override def layer(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] =
    findOrCacheResource(imageId, LayerType)

  override def ancestry(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] =
    findOrCacheResource(imageId, AncestryType)

  override def json(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] =
    findOrCacheResource(imageId, JsonType)

}
