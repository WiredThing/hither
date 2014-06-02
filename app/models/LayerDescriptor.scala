package models

import play.api.libs.json.{JsValue, Json}

case class LayerId(id: String) extends AnyVal

object LayerId {
  implicit val formats = Json.format[LayerId]
}

case class LayerLink(id: String, parent: String)

object LayerLink {
  implicit val formats = Json.format[LayerLink]
}

case class LayerDescriptor(id: LayerLink, layerJson: JsValue)