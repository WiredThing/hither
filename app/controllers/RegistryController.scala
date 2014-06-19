package controllers

import models.ImageId
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsString
import play.api.mvc.{Action, BodyParser, Controller, Result}
import services._
import system.registry.{FileBasedPrivateRegistry, Registry, ResourceType}

object ProductionRegistry extends FileBasedPrivateRegistry 

object RegistryController extends RegistryController {
  override def registry = ProductionRegistry
}

trait RegistryController extends Controller {
  def registry: Registry

  import system.registry.ResourceType._

  def ancestry(imageId: ImageId) = Action.async { implicit request =>
    Logger.info(s"get ancestry for ${imageId.id}")
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

  def layer(imageId: ImageId) = Action.async { implicit request =>
    registry.layer(imageId).map {
      case Some(layer) => feedContent(layer)
      case None => NotFound(s"no json for ${imageId.id}")
    }
  }

  def putJson(imageId: ImageId) = Action(toRegistry(imageId, JsonType, registry)) { request =>
    Logger.info(s"Layer json for ${imageId.id} pushed to registry")
    Ok(JsString(""))
  }

  def putLayer(imageId: ImageId) = Action(toRegistry(imageId, LayerType, registry)) { request =>
    Logger.info(s"Layer ${imageId.id} pushed to registry")
    Ok(JsString(""))
  }


  def putChecksum(imageId: ImageId) = Action(toRegistry(imageId, ChecksumType, registry)) { request =>
    Ok(JsString(""))
  }

  protected def toRegistry(imageId: ImageId, resourceType: ResourceType, registry: Registry): BodyParser[Unit] =
    BodyParser("to registry") { request =>
      registry.sinkFor(imageId, resourceType).map { _ => Right(Unit)}
    }


  protected def feedContent(content: ContentEnumerator): Result = {
    content match {
      case ContentEnumerator(e, contentType, Some(length)) => Ok.feed(e).as(contentType).withHeaders("Content-Length" -> length.toString)
      case ContentEnumerator(e, contentType, None) => Ok.chunked(e).as(contentType)
    }
  }
}
