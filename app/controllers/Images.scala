package controllers

import play.api.mvc.{Action, Controller}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import models.ImageId
import services._
import play.api.libs.json.{Json, JsError}
import play.api.Logger
import system.{Configuration, AppSystem}
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

  def putJson(imageId: ImageId) = Action(parse.file(Configuration.buildRegistryPath(s"${imageId.id}.json").get)) { request =>
    Logger.info(s"Layer json pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }

  def putLayer(imageId: ImageId) = Action(parse.file(Configuration.buildRegistryPath(imageId.id).get)) { request =>
    Logger.info(s"Layer pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }


  def putChecksum(imageId: ImageId) = Action(parse.file(Configuration.buildRegistryPath(s"${imageId.id}.checksum").get)) { request =>
    Logger.info(s"Checksum pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }

  def layer(imageId: ImageId) = Action.async { implicit request =>

    val imageFile = Configuration.buildRegistryPath(imageId.id).get
    val cacheFile = Configuration.buildCachePath(imageId.id).get

    if (imageFile.exists) {
      Logger.info(s"Supplying image ${imageId.id} from local registry")
      Future(Ok.sendFile(imageFile).withHeaders(("Content-Type", "binary/octet-stream"), ("Content-Length", imageFile.length().toString)))
    } else if (cacheFile.exists) {
      Logger.info(s"Supplying image ${imageId.id} from cache")
      Future(Ok.sendFile(cacheFile).withHeaders(("Content-Type", "binary/octet-stream"), ("Content-Length", cacheFile.length().toString)))
    } else {
      val url = s"http://registry-1.docker.io/v1/images/${imageId.id}/layer"

      AppSystem.layerActor ! CacheImage(imageId)

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
}
