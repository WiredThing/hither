package system.registry

import models.ImageId
import play.api.libs.json.{JsString, Json}
import services.ContentEnumerator

import scala.concurrent.{ExecutionContext, Future}

trait PrivateRegistry extends Registry {
  outer =>
  def next: Registry

  def ancestryBuilder = new AncestryBuilder {
    def next = outer.next
  }

  override def ancestry(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    findResource(imageId, AncestryType) flatMap {
      case Some(ce) => Future(Some(ce))

      case None => findResource(imageId, JsonType) flatMap {
        case Some(jsonContent) => ancestryBuilder.buildAncestry(imageId, jsonContent)
        case None => next.ancestry(imageId)
      }
    }
  }
}

trait AncestryBuilder {
  type Ancestry = List[ImageId]

  def next: Registry

  def buildAncestry(imageId: ImageId, jsonContent: ContentEnumerator)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    jsonContent.asString.map(Json.parse(_) \ "parent") flatMap {
      case JsString(parentId) => next.ancestry(ImageId(parentId)) flatMap {
        case Some(ce) => prependImageId(imageId, ce).map(Some(_))
        case None => throw new Exception(s"Could not find ancestry for parent $parentId of layer $imageId")
      }
      case _ => Future(Option(ContentEnumerator(Json.toJson(Array(imageId.id)))))
    }
  }

  def prependImageId(imageId: ImageId, ce: ContentEnumerator)(implicit ctx: ExecutionContext): Future[ContentEnumerator] = {
    for {
      ancestry <- ce.parseJson[Ancestry]
    } yield ContentEnumerator(Json.toJson(imageId +: ancestry))
  }
}
