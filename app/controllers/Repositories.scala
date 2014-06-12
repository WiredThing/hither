package controllers

import scala.concurrent.Future

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsString, Json, JsError, JsSuccess}
import play.api.LoggerLike
import play.api.mvc.Result

import services.ProductionIndexService.ImageResult
import models.Repository
import system.{ProductionLocalIndex, LocalIndex}
import services._

object Repositories extends Repositories {

  override def localIndex: LocalIndex = ProductionLocalIndex

  override def logger: LoggerLike = play.api.Logger

  override def indexService: IndexService = ProductionIndexService
}

trait Repositories extends Controller {

  def localIndex: LocalIndex

  def indexService: IndexService

  def logger: LoggerLike

  def images(repo: Repository) = Action.async { implicit request =>
    getImages(repo).recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }

  def putImages(repo: Repository) = {
    localIndex.createRepoDir(repo)

    Action(parse.file(localIndex.buildImagesPath(repo).file)) { implicit request =>
      logger.info("putImagesNoNamespace")
      NoContent
    }
  }

  def allocateRepo(repo: Repository) = Action(parse.json) { request =>
    indexService.allocateRepo(repo)
    Ok.withHeaders(
      ("X-Docker-Token", s"""signature=123abc,repository="${repo.qualifiedName}",access=write"""),
      ("X-Docker-Endpoints", request.headers("Host"))
    )
  }

  private def getImages(repo: Repository): Future[Result] = {
    val indexFile = localIndex.buildImagesPath(repo)

    if (indexFile.exists()) {
      Future(Ok.sendFile(indexFile.file))
    } else {
      indexService.getImages(repo).map {
        case ImageResult(JsError(errs), _) => BadGateway(errs.toString())
        case ImageResult(JsSuccess(images, _), headers) => Ok(Json.toJson(images)).withHeaders(headers: _*)
      }
    }
  }
}
