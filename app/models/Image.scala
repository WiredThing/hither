package models

import play.api.libs.json._

case class ImageId(id: String) {
  assert(!id.startsWith("\""))
}

object ImageId {
  implicit val formats = new Format[ImageId] {
    override def writes(id: ImageId): JsValue = JsString(id.id)

    override def reads(json: JsValue): JsResult[ImageId] = json match {
      case JsString(id) => JsSuccess(ImageId(id))
      case j => JsError(s"Expected JsString, got $j")
    }
  }
}

case class Image(id: String, checksum: String)

object Image {
  implicit val formats = Json.format[Image]
}
