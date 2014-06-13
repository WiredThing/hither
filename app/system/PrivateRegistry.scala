package system

import java.io.File

import models.ImageId
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.{JsString, Json}
import services.ContentEnumerator
import system.ResourceType._

import scala.concurrent.{ExecutionContext, Future}

object FileBasedPrivateRegsitry extends PrivateRegistry {
  def registryRoot: File = new File(system.Configuration.registryRoot)

  case class RegistryFile(file: File) extends FileLocalSource

  override def ancestryBuilder: AncestryBuilder = ProductionAncestryBuilder

  def localSourceFor(name: String): Option[LocalSource] = RegistryFile(new File(registryRoot, name)).existing

  override def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    val ce = localSourceFor(s"${imageId.id}.${resourceType.name}") match {
      case Some(source) => Some(ContentEnumerator(source.enumerator, resourceType.contentType, Some(source.length())))
      case None => None
    }

    Future.successful(ce)
  }
}

trait PrivateRegistry extends Registry {

  def ancestryBuilder: AncestryBuilder

  def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]

  override def layer(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    findResource(imageId, LayerType)
  }

  override def json(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    findResource(imageId, JsonType)
  }

  override def ancestry(imageId: ImageId)(implicit fallback: AncestryFinder, ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    findResource(imageId, AncestryType) flatMap {
      case Some(ce) => Future(Some(ce))
      case None => constructAncestry(imageId)
    }
  }

  def constructAncestry(imageId: ImageId)(implicit fallback: AncestryFinder, ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    findResource(imageId, JsonType) flatMap {
      case Some(jsonContent) => ancestryBuilder.buildAncestry(imageId, jsonContent)
      case None => Future.successful(None)
    }
  }

  override def putLayer(id: ImageId, body: Iteratee[Array[Byte], Unit]): Unit = ???

  override def putJson(id: ImageId, json: ImageJson): Future[Unit] = ???
}

object ProductionAncestryBuilder extends AncestryBuilder

trait AncestryBuilder {
  type Ancestry = List[ImageId]

  def buildAncestry(imageId: ImageId, jsonContent: ContentEnumerator)(implicit fallback: AncestryFinder, ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    jsonContent.asString.map(Json.parse(_) \ "parent") flatMap {
      case JsString(parentId) => fallback.ancestry(ImageId(parentId)) flatMap {
        case Some(ce) => prependImageId(imageId, ce).map(Some(_))
        case None => throw new Exception(s"Could not find ancestry for parent $parentId of layer $imageId")
      }
      case _ => Future(Option(ContentEnumerator(Json.toJson(Array(imageId.id)))))
    }
  }

  def prependImageId(imageId: ImageId, ce: ContentEnumerator)(implicit ctx: ExecutionContext): Future[ContentEnumerator] = {
    val ancestry = ce.content.run(Iteratee.getChunks).map { byteArrays =>
      val s = byteArrays.map(new String(_)).mkString
      Json.parse(s).as[Ancestry]
    }

    ancestry map { a =>
      ContentEnumerator(Json.toJson(imageId +: a))
    }
  }
}