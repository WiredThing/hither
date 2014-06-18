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

  def images(repo: Repository) = Action { implicit request =>
    val indexFile = localIndex.buildImagesPath(repo)

    if (indexFile.exists()) {
      Ok.sendFile(indexFile.file)
    } else {
      NotFound(s"${repo.qualifiedName}")
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
}
