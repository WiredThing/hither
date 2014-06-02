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


object Repositories extends Controller {

  def imagesNoNamespace(repository: RepositoryName) = Action.async { implicit request =>
    getImages(Repository(None, repository))
  }

  def images(namespace: Namespace, repository: RepositoryName) = Action.async { implicit request =>
    getImages(Repository(Some(namespace), repository))
  }

  private def getImages(repository: Repository): Future[Result] = {
    IndexService.getImages(repository).map {
      case ImageResult(JsError(errs), _) => BadGateway(errs.toString())
      case ImageResult(JsSuccess(images, _), headers) => Ok(Json.toJson(images)).withHeaders(headers: _*)
    }
  }
}
