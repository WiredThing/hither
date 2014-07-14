package controllers

import models.Repository
import play.api._
import play.api.mvc._
import system.Production
import system.index.Index

object Application extends Application {
  override lazy val index: Index = Production.index

  override lazy val logger: LoggerLike = play.api.Logger
}

trait Application extends Controller {

  import play.api.libs.concurrent.Execution.Implicits._

  def index: Index

  def logger: LoggerLike

  def repositories = Action.async { request =>
    index.repositories.map { repos =>
      Ok(views.html.repositories(repos))
    }
  }

  def repository(repo: Repository) = Action.async { request =>
    index.tagSet(repo).map { tags =>
      Ok(views.html.showRepo(repo, tags.toList.sortWith((a, b) => a.name < b.name)))
    }
  }

}
