package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{Json, JsError}

import services._
import scala.concurrent.Future
import play.api.mvc.Result
import play.api.libs.json.JsSuccess
import services.IndexService.ImageResult
import models.{Repository, RepositoryName, Namespace}
import play.api.Logger


object Repositories extends Controller {

  def imagesNoNamespace(repository: RepositoryName) = Action.async { implicit request =>
    getImages(Repository(None, repository))
  }

  def images(namespace: Namespace, repository: RepositoryName) = Action.async { implicit request =>
    getImages(Repository(Some(namespace), repository))
  }

  def putImagesNoNamespace(repository: RepositoryName) = Action(parse.json) { implicit request =>
    Logger.info("TODO - putImagesNoNamespace")
    NoContent
  }

  def putImages(namespace: Namespace, repository: RepositoryName) = Action(parse.json) { implicit request =>
    Logger.info("TODO - putImages")
    NoContent
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
    IndexService.getImages(repository).map {
      case ImageResult(JsError(errs), _) => BadGateway(errs.toString())
      case ImageResult(JsSuccess(images, _), headers) => Ok(Json.toJson(images)).withHeaders(headers: _*)
    }
  }
}
