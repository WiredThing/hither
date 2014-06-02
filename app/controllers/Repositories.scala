package controllers

import play.api.mvc.{Result, Action, Controller}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.{WSRequestHolder, WS}
import scala.concurrent.Future

object Repositories extends Controller {

  def imagesNoNamespace(repository: String) = Action.async { implicit request =>
    getImages(s"http://index.docker.io/v1/repositories/$repository/images")
  }

  def images(namespace:String, repository: String) = Action.async { implicit request =>
    getImages(s"http://index.docker.io/v1/repositories/$namespace/$repository/images")
  }

  private def getImages(url: String): Future[Result] = {
    val request: WSRequestHolder = WS.url(url).withHeaders(("X-Docker-Token", "true"))

    request.get().map {
      response =>
        val headersToCopy = List("x-docker-endpoints", "x-docker-token", "date", "Connection")
        val responseHeaders = headersToCopy.map { key => response.header(key).map((key, _))}.flatten

        Ok(response.json).withHeaders(responseHeaders: _*)
    }
  }
}
