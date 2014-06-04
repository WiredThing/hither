package controllers

import play.api.mvc.{Result, Action, Controller}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import models.ImageId
import play.api.Logger
import system.Registry
import scala.concurrent.Future
import play.api.libs.json.JsString
import scala.Some
import play.api.libs.iteratee.Enumerator
import java.io.FileOutputStream


object Images extends Controller {
  def ancestry(imageId: ImageId) = Action.async { implicit request =>
    Registry.findLocalSource(imageId, Some("ancestry")) match {
      case Some(localSource) =>
        Logger.info(s"Supplying ancestry from ${localSource.source}")
        Future(Ok.sendFile(localSource.file).withHeaders(("Content-Type", "application/json"), ("Content-Length", localSource.length().toString)))

      case None =>
        respondFromUrl(s"${imageId.id}.ancestry", s"http://registry-1.docker.io/v1/images/${imageId.id}/ancestry")
    }
  }

  def json(imageId: ImageId) = Action.async { implicit request =>
    Registry.findLocalSource(imageId, Some("json")) match {
      case Some(localSource) =>
        Logger.info(s"Supplying json from ${localSource.source}")
        Future(Ok.sendFile(localSource.file).withHeaders(("Content-Type", "application/json"), ("Content-Length", localSource.length().toString)))

      case None =>
        respondFromUrl(s"${imageId.id}.json", s"http://registry-1.docker.io/v1/images/${imageId.id}/json")
    }
  }

  def putJson(imageId: ImageId) = Action(parse.file(Registry.buildRegistryPath(s"${imageId.id}.json").file)) { request =>
    Logger.info(s"Layer json pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }

  def putLayer(imageId: ImageId) = Action(parse.file(Registry.buildRegistryPath(imageId.id).file)) { request =>
    Logger.info(s"Layer pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }


  def putChecksum(imageId: ImageId) = Action(parse.file(Registry.buildRegistryPath(s"${imageId.id}.checksum").file)) { request =>
    Logger.info(s"Checksum pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }

  def layer(imageId: ImageId) = Action.async { implicit request =>
    Registry.findLocalSource(imageId) match {
      case Some(localSource) =>
        Logger.info(s"Supplying image from ${localSource.source}")
        Future(Ok.sendFile(localSource.file).withHeaders(("Content-Type", "binary/octet-stream"), ("Content-Length", localSource.length().toString)))

      case None =>
        respondFromUrl(imageId.id, s"http://registry-1.docker.io/v1/images/${imageId.id}/layer")
    }
  }

  def respondFromUrl(cacheFileName:String, url: String): Future[Result] = {
    WS.url(url).getStream().map {
      case (response, body) =>

        // Check that the response was successful
        if (response.status == 200) {
          // Get the content type
          val contentType = response.headers.get("Content-Type").flatMap(_.headOption)
            .getOrElse("binary/octet-stream")

          val cacheFile = Registry.buildCachePath(cacheFileName)
          val os = new FileOutputStream(cacheFile.file)

          val fileWriter :Enumerator[Array[Byte]] = body.map { bytes =>
            os.write(bytes)
            bytes
          }

          fileWriter.onDoneEnumerating(os.close())

          // If there's a content length, send that, otherwise return the body chunked
          response.headers.get("Content-Length") match {
            case Some(Seq(length)) =>
              Ok.feed(fileWriter).as(contentType).withHeaders("Content-Length" -> length)
            case _ =>
              Ok.chunked(fileWriter).as(contentType)
          }
        } else {
          BadGateway
        }
    }
  }
}
