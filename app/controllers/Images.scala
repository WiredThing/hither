package controllers

import models.ImageId
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{Action, Controller, Result}
import services.{ContentEnumerator, ImageService, NotFoundException, ProductionImageService}
import system.RegistryType

object ImagesController extends Images {
  override lazy val imageService = ProductionImageService
}

trait Images extends Controller {
  val imageService: ImageService

  import RegistryType.{ChecksumType, JsonType, LayerType}

  def ancestry(imageId: ImageId) = Action.async { implicit request =>
    Logger.info(s"get ancestry for ${imageId.id}")
    imageService.lookupAncestry(imageId).map { l => Ok(Json.toJson(l))}.recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }

  def json(imageId: ImageId) = Action.async { implicit request =>
    imageService.findData(imageId, JsonType).map(feedContent).recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }

  def layer(imageId: ImageId) = Action.async { implicit request =>
    imageService.findData(imageId, LayerType, "binary/octet-stream").map(feedContent).recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }

  def putJson(imageId: ImageId) = Action(parse.file(imageService.fileFor(imageId, JsonType))) { request =>
    Logger.info(s"Layer json pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }

  def putLayer(imageId: ImageId) = Action(parse.file(imageService.fileFor(imageId, LayerType))) { request =>
    Logger.info(s"Layer pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }


  def putChecksum(imageId: ImageId) = Action(parse.file(imageService.fileFor(imageId, ChecksumType))) { request =>
    Logger.info(s"Checksum pushed to ${request.body.getAbsolutePath}")
    Ok(JsString(""))
  }

  def feedContent(content: ContentEnumerator): Result = {
    content match {
      case ContentEnumerator(e, contentType, Some(length)) => Ok.feed(e).as(contentType).withHeaders("Content-Length" -> length.toString)
      case ContentEnumerator(e, contentType, None) => Ok.chunked(e).as(contentType)
    }
  }
}
