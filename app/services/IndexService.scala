package services

import scala.concurrent.Future
import play.api.libs.ws.{WS, WSRequestHolder}
import play.api.libs.json.{Json, JsResult}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Logger


case class ImageResult(images: JsResult[List[Image]], headers: List[(String, String)])

case class Image(id: String, checksum: String)

case class Namespace(name: String) extends AnyVal

case class RepositoryName(name: String) extends AnyVal

case class Repository(namespace: Option[Namespace], repoName: RepositoryName) {
  val urlString = namespace.map { ns =>
    s"${ns.name}/${repoName.name}"
  }.getOrElse {
    repoName.name
  }
}

object Image {
  implicit val formats = Json.format[Image]
}

object IndexService {
  def getImages(repo: Repository): Future[ImageResult] = {
    val url = s"http://index.docker.io/v1/repositories/${repo.urlString}/images"
    Logger.info(url)
    val request: WSRequestHolder = WS.url(url).withHeaders(("X-Docker-Token", "true"))

    request.get().map {
      response =>
        val headersToCopy = List("x-docker-endpoints", "x-docker-token", "date", "Connection")
        val responseHeaders = headersToCopy.map { key => response.header(key).map((key, _))}.flatten

        ImageResult(response.json.validate[List[Image]], responseHeaders)
    }
  }
}
