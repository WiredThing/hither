package controllers

import play.api.mvc.{Action, Controller}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import play.api.Logger

object Tags extends Controller {

  def tagsWithoutNamespace(repository: String) = Action.async { implicit request =>
    Logger.info(s"tags for $repository")
    WS.url(s"http://registry-1.docker.io/v1/repositories/$repository/tags").get().map {
      response => Ok(response.json)
    }
  }

  def tags(namespace: String, repository: String) = Action.async { implicit request =>
    Logger.info(s"tags for $namespace/$repository")
    WS.url(s"http://registry-1.docker.io/v1/repositories/$namespace/$repository/tags").get().map {
      response => Ok(response.json)
    }
  }

  def tagNameWithoutNamespace(repository: String, tagName: String) = Action.async { implicit request =>
    Logger.info(s"tags $tagName for $repository")
    WS.url(s"http://registry-1.docker.io/v1/repositories/$repository/tags/$tagName").get().map {
      response => Ok(response.json)
    }
  }

  def tagName(namespace: String, repository: String, tagName: String) = Action.async { implicit request =>
    Logger.info(s"tags $tagName for $namespace/$repository")
    WS.url(s"http://registry-1.docker.io/v1/repositories/$namespace/$repository/tags/$tagName").get().map {
      response => Ok(response.json)
    }
  }

}
