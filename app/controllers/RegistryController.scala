package controllers


import play.api.LoggerLike
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller

import scala.util.Try


object RegistryController extends RegistryController {

  import system.Production

  override def registry = Production.registry

  override lazy val logger: LoggerLike = play.api.Logger
}

trait RegistryController extends Controller with ContentFeeding {

  import models.ImageId
  import play.api.libs.json.JsString
  import play.api.mvc.{Action, BodyParser}
  import system.registry.ResourceType._
  import system.registry.{Registry, ResourceType}

  def registry: Registry

  def logger: LoggerLike

  def ancestry(imageId: ImageId) = Action.async { implicit request =>
    logger.debug(s"get ancestry for ${imageId.id}")
    registry.ancestry(imageId).map {
      case Some(ce) => feedContent(ce)
      case None => NotFound(s"no ancestry for ${imageId.id}")
    }
  }

  def json(imageId: ImageId) = Action.async { implicit request =>
    registry.json(imageId).map {
      case Some(js) => feedContent(js)
      case None => NotFound(s"no json for ${imageId.id}")
    }
  }

  def layerHead(imageId: ImageId) = Action.async { implicit request =>
    registry.layerHead(imageId).map {
      case Some(l) => Ok("").withHeaders("Content-Type" -> "binary/octet-stream", "Content-Length" -> l.toString)
      case None => NotFound
    }
  }

  def layer(imageId: ImageId) = Action.async { implicit request =>
    registry.layer(imageId).map {
      case Some(layer) => feedContent(layer)
      case None => NotFound(s"no json for ${imageId.id}")
    }
  }

  def putJson(imageId: ImageId) = Action(toRegistry(imageId, JsonType, registry)) { request =>
    logger.debug(s"Layer json for ${imageId.id} pushed to registry")
    Ok(JsString(""))
  }

  def putLayer(imageId: ImageId) = Action(toRegistry(imageId, LayerType, registry)) { request =>
    logger.debug(s"Layer ${imageId.id} pushed to registry")
    Ok(JsString(""))
  }


  def putChecksum(imageId: ImageId) = Action(toRegistry(imageId, ChecksumType, registry)) { request =>
    Ok(JsString(""))
  }

  protected def toRegistry(imageId: ImageId, resourceType: ResourceType, registry: Registry): BodyParser[Unit] =
    BodyParser("to registry") { request =>
      val contentLength = request.headers.get("Content-Length").flatMap(s => Try(s.toLong).toOption)
      registry.sinkFor(imageId, resourceType, contentLength).map { _ => Right(Unit)}
    }
}
