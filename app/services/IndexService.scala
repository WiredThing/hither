package services

import scala.concurrent.Future
import play.api.libs.ws.{WS, WSRequestHolder}
import play.api.libs.json.JsResult
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import models.{Image, Repository}
import system.Configuration

object ProductionIndexService extends IndexService {
  override def indexHostName: String = Configuration.indexHostName
}

trait IndexService {

  def indexHostName:String

  case class ImageResult(images: JsResult[List[Image]], headers: List[(String, String)])

  def getImages(repo: Repository): Future[ImageResult] = {
    val url = s"http://$indexHostName/v1/repositories/${repo.qualifiedName}/images"
    val request: WSRequestHolder = WS.url(url).withHeaders(("X-Docker-Token", "true"))

    request.get().map {
      response =>
        response.status match {
          case s if s >= 200 && s <= 299 =>
            val headersToCopy = List("x-docker-token", "date", "Connection")
            val responseHeaders = headersToCopy.map { key => response.header(key).map((key, _))}.flatten

            ImageResult(response.json.validate[List[Image]], responseHeaders)

          case 404 => throw NotFoundException(url)
        }
    }
  }

  def allocateRepo(repo: Repository): Unit = {
    Configuration.buildRepoIndexPath(repo)
  }
}
