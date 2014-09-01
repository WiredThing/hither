package controllers

import models.Repository
import play.api.LoggerLike
import play.api.mvc.{Action, Controller}
import system.Production
import system.index.Index

import scala.concurrent.Future

object RepositoryController extends RepositoryController {
  override lazy val index: Index = Production.index

  override lazy val logger: LoggerLike = play.api.Logger
}

trait RepositoryController extends Controller {

  import play.api.libs.concurrent.Execution.Implicits._

  def index: Index

  def logger: LoggerLike

  def repositories = Action.async { request =>
    index.repositories.map { repos =>
      Ok(views.html.repositories(repos))
    }
  }

  def show(repo: Repository) = Action.async { request =>
    index.exists(repo).flatMap {
      case true => index.tagSet(repo).map { tags =>
        Ok(views.html.showRepo(repo, tags.toList.sortWith((a, b) => a.name < b.name)))
      }
      case false => Future(Ok(views.html.noRepo(repo)))
    }
  }

  def create(repo: Repository) = Action.async { implicit request =>
    index.create(repo).map(_ => Redirect(routes.RepositoryController.repositories))
  }
}
