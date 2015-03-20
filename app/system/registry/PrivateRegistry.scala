package system.registry

import models.ImageId
import play.api.Logger
import play.api.cache.Cache
import play.api.libs.json.{JsValue, JsResultException, JsString, Json}
import services.ContentEnumerator
import play.api.Play.current

import scala.concurrent.{ExecutionContext, Future}

trait PrivateRegistry extends Registry {
  outer =>

  def ancestryBuilder = new AncestryBuilder {
    def registry = outer
  }

  import system.registry.ResourceType._

  override def ancestry(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    val key: String = s"ancestry.${imageId.id}"
    Cache.get(key) match {
      case Some(json: JsValue) => Logger.debug(s"serving ancestry for $imageId from cache"); Future(Some(ContentEnumerator(json)))
      case _ => findResource(imageId, JsonType) flatMap {
        case Some(jsonContent) =>
          Logger.info(s"building ancestry for ${imageId.id}")
          val json: Future[Option[JsValue]] = ancestryBuilder.buildAncestry(imageId, jsonContent).flatMap {
            case Some(ce) => ce.asJson.map { json =>
              Cache.set(key, json, 600)
              Some(json)
            }
            case None => Future(None)
          }
          json.map(_.map(ContentEnumerator(_)))
        case None =>
          Logger.warn(s"can't find json file for ${imageId.id} whilst building ancestry")
          Future(None)
      }
    }
  }
}

trait AncestryBuilder {
  type Ancestry = List[String]

  def registry: Registry

  def buildAncestry(imageId: ImageId, jsonContent: ContentEnumerator)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    jsonContent.asJson.map(_ \ "parent") flatMap {
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
