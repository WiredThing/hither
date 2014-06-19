package controllers

import models.Repository
import play.api.LoggerLike
import play.api.mvc.{Action, Controller}
import services._
import system.{LocalIndex, ProductionLocalIndex}

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
