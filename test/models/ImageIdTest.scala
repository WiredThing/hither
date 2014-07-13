package models

import org.scalatest.{Matchers, FlatSpec}
import play.api.libs.json._

class ImageIdTest extends FlatSpec with Matchers {
  "An ImageId" should "convert to Json as a JsString of the id" in {
    val imageId = ImageId("foo")
    Json.toJson(imageId) shouldBe JsString("foo")
  }

  it should "validate from a JsString" in {
    val json = JsString("foo")
    json.validate[ImageId] shouldBe JsSuccess(ImageId("foo"))
  }

  it should "not validate from a JsObject" in {
    val json = JsObject(Seq(("foo", JsString("bar"))))
    json.validate[ImageId] shouldBe a[JsError]
  }
}
