package models

import play.api.libs.json.Json

case class Image(id: String, checksum: String)

object Image {
  implicit val formats = Json.format[Image]
}
