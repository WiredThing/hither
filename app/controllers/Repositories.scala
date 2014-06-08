package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsString, Json, JsError, JsSuccess}

import services._
import scala.concurrent.Future
import play.api.mvc.Result
import services.IndexService.ImageResult
import models.{Repository, RepositoryName, Namespace}
import play.api.Logger
import system.LocalIndex


object Repositories extends Controller {

  def images(repo: Repository) = Action.async { implicit request =>
    getImages(repo).recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }


  def putImages(repo: Repository) = {
    LocalIndex.createRepoDir(repo)

    Action(parse.file(LocalIndex.buildImagesPath(repo).file)) { implicit request =>
      Logger.info("putImagesNoNamespace")
      NoContent
    }
  }

  def allocateRepo(repo: Repository) = Action(parse.json) { request =>
    IndexService.allocateRepo(repo)
    Ok.withHeaders(
      ("X-Docker-Token", s"""signature=123abc,repository="${repo.qualifiedName}",access=write"""),
      ("X-Docker-Endpoints", request.headers("Host"))
    )
  }

  private def getImages(repo: Repository): Future[Result] = {
    val indexFile = LocalIndex.buildImagesPath(repo)

    if (indexFile.exists()) {
      Future(Ok.sendFile(indexFile.file))
    } else {
      IndexService.getImages(repo).map {
        case ImageResult(JsError(errs), _) => BadGateway(errs.toString())
        case ImageResult(JsSuccess(images, _), headers) => Ok(Json.toJson(images)).withHeaders(headers: _*)
      }
    }
  }
}
