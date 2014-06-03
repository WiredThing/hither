package controllers

import play.api.mvc.{Result, Action, Controller}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import models.ImageId
import services._
import play.api.libs.json.{Json, JsError}
import play.api.Logger
import system.{Registry, Configuration, AppSystem}
import scala.concurrent.Future
import services.NotFoundResult
import play.api.libs.json.JsSuccess
import services.JsonResult
import actors.CacheImage
import play.api.libs.json.JsString
import scala.Some


object Images extends Controller {
  def ancestry(imageId: ImageId) = Action.async { implicit request =>
    RegistryService.ancestry(imageId).map {
      case JsonResult(JsError(errs), _) => BadGateway(errs.toString())
      case JsonResult(JsSuccess(images, _), headers) => {
        images.foreach { imageId =>
          AppSystem.layerActor ! CacheImage(imageId)
        }
        Ok(Json.toJson(images.map(_.id))).withHeaders(headers: _*)
      }
      case NotFoundResult() => NotFound
      case ErrorResult(code) => Status(code)
    }
  }

  def json(imageId: ImageId) = Action.async { implicit request =>
    RegistryService.json(imageId).map {
      case JsonResult(JsError(errs), _) => BadGateway(errs.toString())
      case JsonResult(JsSuccess(layerDescriptor, _), headers) => Ok(layerDescriptor.layerJson).withHeaders(headers: _*)
      case NotFoundResult() => NotFound
      case ErrorResult(code) => Status(code)
    }
  }

  def putJson(imageId: ImageId) = Action(parse.file(Registry.buildRegistryPath(s"${imageId.id}.json").mkdirs.file)) { request =>
    Logger.info(s"Layer json pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }

  def putLayer(imageId: ImageId) = Action(parse.file(Registry.buildRegistryPath(imageId.id).mkdirs.file)) { request =>
    Logger.info(s"Layer pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }


  def putChecksum(imageId: ImageId) = Action(parse.file(Registry.buildRegistryPath(s"${imageId.id}.checksum").mkdirs.file)) { request =>
    Logger.info(s"Checksum pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }

  def layer(imageId: ImageId) = Action.async { implicit request =>
    Registry.findLocalSource(imageId) match {
      case Some(localSource) =>
        Logger.info(s"Supplying image ${imageId.id} from ${localSource.source}")
        Future(Ok.sendFile(localSource.file).withHeaders(("Content-Type", "binary/octet-stream"), ("Content-Length", localSource.length().toString)))

      case None =>
        AppSystem.layerActor ! CacheImage(imageId)
        respondFromUrl(s"http://registry-1.docker.io/v1/images/${imageId.id}/layer")
    }
  }

  def respondFromUrl(url: String): Future[Result] = {
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
