package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.Logger
import play.api.mvc.{Result, Action, Controller}
import play.api.libs.json.{Json, JsString}

import system.LocalRegistry
import models.ImageId
import services.{ContentEnumerator, ImageService, NotFoundException}

object Images extends Images {
  override def imageService = ImageService
}

trait Images extends Controller {
  def imageService: ImageService

  def ancestry(imageId: ImageId) = Action.async { implicit request =>
    Logger.info(s"get ancestry for ${imageId.id}")
    imageService.buildAncestry(imageId).map { l => Ok(Json.toJson(l))}.recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }

  def json(imageId: ImageId) = Action.async { implicit request =>
    imageService.findData(imageId, "json").map(feedContent).recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }

  def layer(imageId: ImageId) = Action.async { implicit request =>
    imageService.findData(imageId, "layer", "binary/octet-stream").map(feedContent).recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }


  def feedContent(content: ContentEnumerator): Result = {
    content match {
      case ContentEnumerator(e, contentType, Some(length)) => Ok.feed(e).as(contentType).withHeaders("Content-Length" -> length.toString)
      case ContentEnumerator(e, contentType, None) => Ok.chunked(e).as(contentType)
    }
  }

  def putJson(imageId: ImageId) = Action(parse.file(LocalRegistry.buildRegistryPath(s"${imageId.id}.json").file)) { request =>
    Logger.info(s"Layer json pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }


  def putLayer(imageId: ImageId) = Action(parse.file(LocalRegistry.buildRegistryPath(s"${imageId.id}.layer").file)) { request =>
    Logger.info(s"Layer pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }

  def putChecksum(imageId: ImageId) = Action(parse.file(LocalRegistry.buildRegistryPath(s"${imageId.id}.checksum").file)) { request =>
    Logger.info(s"Checksum pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }
}
