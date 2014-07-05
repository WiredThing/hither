package system.registry

import java.io.OutputStream

import models.ImageId
import play.api.Logger
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.{JsResultException, JsString, Json}
import services.ContentEnumerator

import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

trait PrivateRegistry extends Registry {
  outer =>

  def ancestryBuilder = new AncestryBuilder {
    def registry = outer
  }

  import ResourceType._

  override def ancestry(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    findResource(imageId, JsonType) flatMap {
      case Some(jsonContent) =>
        Logger.info(s"building ancestry for ${imageId.id}")
        ancestryBuilder.buildAncestry(imageId, jsonContent)
      case None =>
        Logger.warn(s"can't find json file for ${imageId.id} whilst building ancestry")
        Future(None)
    }
  }

  def outputStreamFor(id: ImageId, resourceType: ResourceType): OutputStream

  override def sinkFor(id: ImageId, resourceType: ResourceType)(implicit ctx:ExecutionContext): Iteratee[Array[Byte], Unit] = {
    val os = outputStreamFor(id, resourceType)
    Iteratee.fold[Array[Byte], OutputStream](os) { (os, data) =>
      os.write(data)
      os
    }.map { os =>
      os.close()
      Right(Unit)
    }
  }
}

trait AncestryBuilder {
  type Ancestry = List[String]

  def registry: Registry

  def buildAncestry(imageId: ImageId, jsonContent: ContentEnumerator)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    jsonContent.asString.map(Json.parse(_) \ "parent") flatMap {
      case JsString(parentId) => registry.ancestry(ImageId(parentId)) flatMap {
        case Some(ce) => prependImageId(imageId, ce).map(Some(_))
        case None => throw new Exception(s"Could not find ancestry for parent $parentId of layer $imageId")
      }
      case _ => Future(Option(ContentEnumerator(Json.toJson(List(imageId.id)))))
    }
  }

  def prependImageId(imageId: ImageId, ce: ContentEnumerator)(implicit ctx: ExecutionContext): Future[ContentEnumerator] = {
    try {
      for {
        ancestry <- ce.parseJson[Ancestry]
      } yield ContentEnumerator(Json.toJson(imageId.id +: ancestry))
    } catch {
      case jre: JsResultException => Logger.error(s"Could not parse json into id list", jre); throw jre
    }
  }
}
