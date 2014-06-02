package controllers

import play.api.mvc.{Action, Controller}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import play.api.Logger


object Images extends Controller {
  def ancestry(imageId: String) = Action.async { implicit request =>
    Logger.info(s"ancestry for $imageId")
    WS.url(s"http://registry-1.docker.io/v1/images/$imageId/ancestry").get().map {
      response => Ok(response.json)
    }
  }

  def json(imageId: String) = Action.async { implicit request =>
    Logger.info(s"json for $imageId")
    WS.url(s"http://registry-1.docker.io/v1/images/$imageId/json").get().map {
      response => Ok(response.json)
    }
  }



  def layer(imageId: String) = Action.async { implicit request =>
    Logger.info(s"layer for $imageId")
    val url = s"http://registry-1.docker.io/v1/images/$imageId/layer"

    // Make the request
    WS.url(url).getStream().map {
      case (response, body) =>

        // Check that the response was successful
        if (response.status == 200) {

          // Get the content type
          val contentType = response.headers.get("Content-Type").flatMap(_.headOption)
            .getOrElse("application/octet-stream")

          // If there's a content length, send that, otherwise return the body chunked
          response.headers.get("Content-Length") match {
            case Some(Seq(length)) =>
              Ok.feed(body).as(contentType).withHeaders("Content-Length" -> length)
            case _ =>
              Ok.chunked(body).as(contentType)
          }
        } else {
          BadGateway
        }
    }
  }
}
