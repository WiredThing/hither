package services

import play.api.Logger
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.{JsValue, Json, Reads}

import scala.concurrent.{ExecutionContext, Future}

case class ContentEnumerator(content: Enumerator[Array[Byte]], contentType: String, contentLength: Option[Long]) {
  def asString(implicit ctx: ExecutionContext): Future[String] =
    content.run(Iteratee.getChunks).map(_.map(new String(_)).mkString)

  def asJson(implicit ctx: ExecutionContext): Future[JsValue] = asString.map(Json.parse)

  def parseJson[A](implicit ctx: ExecutionContext, reads: Reads[A]): Future[A] = asJson.map(_.as[A])
}

object ContentEnumerator {

  import java.io.ByteArrayInputStream

  def apply(js: JsValue)(implicit ctx: ExecutionContext): ContentEnumerator = {
    val bytes = Json.stringify(js).getBytes()
    val e = Enumerator.fromStream(new ByteArrayInputStream(bytes))

    ContentEnumerator(e, "application/json", Some(bytes.length))
  }
}
