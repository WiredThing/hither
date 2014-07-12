package controllers

import play.api._
import play.api.mvc._
import system.{Production, Index}

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

}
