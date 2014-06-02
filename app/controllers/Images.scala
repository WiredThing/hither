package controllers

import play.api.mvc.{Action, Controller}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import models.ImageId
import services.{ServiceResult, RegistryService}
import play.api.libs.json.{JsString, Json, JsError, JsSuccess}
import java.io.File
import play.api.Logger


object Images extends Controller {
  def ancestry(imageId: ImageId) = Action.async { implicit request =>
    RegistryService.ancestry(imageId).map {
      case ServiceResult(JsError(errs), _) => BadGateway(errs.toString())
      case ServiceResult(JsSuccess(images, _), headers) => Ok(Json.toJson(images.map(_.id))).withHeaders(headers: _*)
    }
  }

  def json(imageId: ImageId) = Action.async { implicit request =>
    RegistryService.json(imageId).map {
      case ServiceResult(JsError(errs), _) => BadGateway(errs.toString())
      case ServiceResult(JsSuccess(layerDescriptor, _), headers) => Ok(layerDescriptor.layerJson).withHeaders(headers: _*)
    }
  }

  def putJson(imageId:ImageId) = Action(parse.file(new File(s"/tmp/${imageId.id}.json"))) { request =>
    Logger.info(s"Layer json pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }

  def putLayer(imageId: ImageId) = Action(parse.file(new File(s"/tmp/${imageId.id}"))) { request =>
    Logger.info(s"Layer pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }

  def layer(imageId: ImageId) = Action.async { implicit request =>
    val url = s"http://registry-1.docker.io/v1/images/${imageId.id}/layer"

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
