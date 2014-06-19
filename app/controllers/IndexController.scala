package controllers

import models.Repository
import play.api.LoggerLike
import play.api.mvc.{Action, Controller}
import system.{Index, LocalIndex, ProductionLocalIndex}

object IndexController extends IndexController {

  override lazy val localIndex: LocalIndex = ProductionLocalIndex

  override lazy val logger: LoggerLike = play.api.Logger

  override lazy val index = ???
}

trait IndexController extends Controller {
  def localIndex: LocalIndex

  def index:Index

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
    localIndex.createRepoDir(repo)
    Ok.withHeaders(
      ("X-Docker-Token", s"""signature=123abc,repository="${repo.qualifiedName}",access=write"""),
      ("X-Docker-Endpoints", request.headers("Host"))
    )
  }
}
