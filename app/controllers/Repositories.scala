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
import system.Index


object Repositories extends Controller {

  def imagesNoNamespace(repository: RepositoryName) = Action.async { implicit request =>
    getImages(Repository(None, repository)).recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }

  def images(namespace: Namespace, repository: RepositoryName) = Action.async { implicit request =>
    getImages(Repository(Some(namespace), repository)).recover {
      case NotFoundException(message) => NotFound(JsString(message))
    }
  }

  def putImagesNoNamespace(repository: RepositoryName) = {
    val repo = Repository(None, repository)
    Index.createRepoDir(repo)

    Action(parse.file(Index.buildImagesPath(repo).file)) { implicit request =>
      Logger.info("putImagesNoNamespace")
      NoContent
    }
  }

  def putImages(namespace: Namespace, repository: RepositoryName) = {
    val repo = Repository(Some(namespace), repository)
    Index.createRepoDir(repo)

    Action(parse.file(Index.buildImagesPath(repo).file)) { implicit request =>
      Logger.info("putImagesNoNamespace")
      NoContent
    }
  }

  def allocateRepoWithoutNamespace(repositoryName: RepositoryName) = Action(parse.json) { request =>
    val repo = Repository(None, repositoryName)

    IndexService.allocateRepo(repo)

    Ok.withHeaders(
      ("X-Docker-Token", s"""signature=123abc,repository="${repo.qualifiedName}",access=write"""),
      ("X-Docker-Endpoints", request.headers("Host"))
    )
  }

  def allocateRepo(namespace: Namespace, repositoryName: RepositoryName) = Action(parse.json) { request =>
    val repo = Repository(Some(namespace), repositoryName)

    Ok.withHeaders(
      ("X-Docker-Token", s"""signature=123abc,repository="${repo.qualifiedName}",access=write"""),
      ("X-Docker-Endpoints", request.headers("Host"))
    )
  }

  private def getImages(repository: Repository): Future[Result] = {
    val indexFile = Index.buildImagesPath(repository)

    if (indexFile.exists()) {
      Future(Ok.sendFile(indexFile.file))
    } else {
      IndexService.getImages(repository).map {
        case ImageResult(JsError(errs), _) => BadGateway(errs.toString())
        case ImageResult(JsSuccess(images, _), headers) => Ok(Json.toJson(images)).withHeaders(headers: _*)
      }
    }
  }
}
