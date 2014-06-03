package services

import scala.concurrent.Future
import play.api.libs.ws.{WS, WSRequestHolder}
import play.api.libs.json.JsResult
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import models.{Image, Repository}


object IndexService {
  case class ImageResult(images: JsResult[List[Image]], headers: List[(String, String)])

  def getImages(repo: Repository): Future[ImageResult] = {
    val url = s"http://index.docker.io/v1/repositories/${repo.qualifiedName}/images"
    val request: WSRequestHolder = WS.url(url).withHeaders(("X-Docker-Token", "true"))

    request.get().map {
      response =>
        val headersToCopy = List("x-docker-endpoints", "x-docker-token", "date", "Connection")
        val responseHeaders = headersToCopy.map { key => response.header(key).map((key, _))}.flatten

        ImageResult(response.json.validate[List[Image]], responseHeaders)
    }
  }
}
