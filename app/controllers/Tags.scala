package controllers

import play.api.mvc.{Result, Action, Controller}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import models.{Repository, Namespace, RepositoryName}
import play.api.libs.json.{Json, JsString}
import system.Index
import java.io.{File, FileFilter}
import scala.concurrent.Future
import scala.io.Source
import play.api.Logger

object Tags extends Controller {
  def tagsWithoutNamespace(repository: RepositoryName) = Action.async { implicit request =>
    val tagsDir = Index.buildTagsDir(Repository(None, repository))
    Logger.info("Tags dir is " + tagsDir.getAbsolutePath)
    if (tagsDir.exists()) {
      feedTagsFromLocal(tagsDir)
    } else {
      WS.url(s"http://registry-1.docker.io/v1/repositories/$repository/tags").get().map {
        response => Ok(response.json)
      }
    }
  }

  def tags(namespace: Namespace, repository: RepositoryName) = Action.async { implicit request =>
    val tagsDir = Index.buildTagsDir(Repository(Some(namespace), repository))
    Logger.info("Tags dir is " + tagsDir.getAbsolutePath)
    if (tagsDir.exists()) {
      feedTagsFromLocal(tagsDir)
    } else {
      WS.url(s"http://registry-1.docker.io/v1/repositories/${namespace.name}/${repository.name}/tags").get().map {
        response => Ok(response.json)
      }
    }
  }

  def feedTagsFromLocal(tagsDir: File): Future[Result] = {
    val filter = new FileFilter {
      override def accept(f: File): Boolean = f.isFile
    }

    def trimQuotes(s: String): String = if (s.startsWith("\"") && s.endsWith("\"")) s.substring(1, s.length - 1) else s

    val tags = tagsDir.listFiles(filter).map { tagFile =>
      (tagFile.getName(), trimQuotes(Source.fromFile(tagFile).mkString))
    }

    Future(Ok(Json.toJson(Map(tags: _*))))
  }

  def tagNameWithoutNamespace(repository: RepositoryName, tagName: String) = Action.async { implicit request =>
    WS.url(s"http://registry-1.docker.io/v1/repositories/$repository/tags/$tagName").get().map {
      response => Ok(response.json)
    }
  }

  def tagName(namespace: Namespace, repository: RepositoryName, tagName: String) = Action.async { implicit request =>
    WS.url(s"http://registry-1.docker.io/v1/repositories/$namespace/$repository/tags/$tagName").get().map {
      response => Ok(response.json)
    }
  }

  def putTagNameWithoutNamespace(repository: RepositoryName, tagName: String) = {
    Index.buildTagsDir(Repository(None, repository)).mkdirs()

    Action(parse.file(Index.buildTagPath(Repository(None, repository), tagName).file)) { request =>
      Ok(JsString(""))
    }
  }

  def putTagName(namespace: Namespace, repository: RepositoryName, tagName: String) = {
    Index.buildTagsDir(Repository(Some(namespace), repository)).mkdirs()
    Action(parse.file(Index.buildTagPath(Repository(Some(namespace), repository), tagName).file)) { request =>
      Ok(JsString(""))
    }
  }

}


