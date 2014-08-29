package controllers

import models.Repository
import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action, Controller}

object RepositoryController extends RepositoryController

trait RepositoryController extends Controller {

  val userForm = Form(mapping("name" -> text)(Repository.parse _)(Repository.qualifiedName _))

  def add = Action {
    Ok(views.html.addRepository(userForm))
  }

  def create = Action { implicit request =>
    userForm.bindFromRequest.fold(
      formWithErrors => ???,
      repo => Redirect("/")
    )

  }
}
