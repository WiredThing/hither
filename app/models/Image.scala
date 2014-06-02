package models

import play.api.libs.json.Json

case class ImageId(id: String) extends AnyVal

object ImageId {
  implicit val formats = Json.format[ImageId]
}

case class Image(id: String, checksum: String)

object Image {
  implicit val formats = Json.format[Image]
}
