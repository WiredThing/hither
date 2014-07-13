package models

import play.api.libs.json._

case class Tag(name: String, version: ImageId)

object Tag {
  implicit val formats = new Format[Tag] {
    override def writes(tag: Tag): JsValue = JsObject(Seq((tag.name, Json.toJson(tag.version))))

    override def reads(json: JsValue): JsResult[Tag] = json match {
      case JsObject(Seq((name, JsString(id)))) => JsSuccess(Tag(name, ImageId(id)))
      case x => JsError(s"Expected a {name, id} object but got $x")
    }
  }
}
