package controllers

import scala.io.Source
import scala.concurrent.Future

import java.io.{FileOutputStream, File, FileFilter}

import play.api.mvc.{Request, Result, Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsValue, Json, JsString}
import play.api.Logger

import models.{Repository, Namespace, RepositoryName}
import system.LocalIndex
import services.TagService

object Tags extends Controller {
  def tagsWithoutNamespace(repoName: RepositoryName) = Action.async { implicit request =>
    val tagsDir = LocalIndex.buildTagsDir(Repository(repoName))
    Logger.info("Tags dir is " + tagsDir.getAbsolutePath)
    if (tagsDir.exists()) {
      feedTagsFromLocal(tagsDir)
    } else {
      TagService.getTags(repoName).map(Ok(_))
    }
  }

  def tags(namespace: Namespace, repoName: RepositoryName) = Action.async { implicit request =>
    val tagsDir = LocalIndex.buildTagsDir(Repository(namespace, repoName))
    Logger.info("Tags dir is " + tagsDir.getAbsolutePath)
    if (tagsDir.exists()) {
      feedTagsFromLocal(tagsDir)
    } else {
      TagService.getTags(namespace, repoName).map(Ok(_))
    }
  }

  def feedTagsFromLocal(tagsDir: File): Future[Result] = {
    val filter = new FileFilter {
      override def accept(f: File): Boolean = f.isFile
    }

    val tags = tagsDir.listFiles(filter).map { tagFile =>
      (tagFile.getName(), Source.fromFile(tagFile).mkString)
    }

    Future(Ok(Json.toJson(Map(tags: _*))))
  }

  def tagNameWithoutNamespace(repoName: RepositoryName, tagName: String) = Action.async { implicit request =>
    TagService.getTag(repoName, tagName).map(Ok(_))
  }

  def tagName(namespace: Namespace, repository: RepositoryName, tagName: String) = Action.async { implicit request =>
    TagService.getTag(namespace, repository, tagName).map(Ok(_))
  }

  def putTagNameWithoutNamespace(repoName: RepositoryName, tagName: String) = {
    Action(parse.json) { request =>
      writeTagsfile(Repository(repoName), tagName, request)
    }
  }

  def putTagName(namespace: Namespace, repository: RepositoryName, tagName: String) = {
    Action(parse.json) { request =>
      writeTagsfile(Repository(namespace, repository), tagName, request)
    }
  }

  def writeTagsfile(repository: Repository, tagName: String, request: Request[JsValue]): Result = {
    request.body match {
      case JsString(value) =>
        Logger.info(s"tag value is $value")
        val tagsDir = LocalIndex.buildTagsDir(repository)
        tagsDir.mkdirs()
        val fos = new FileOutputStream(new File(tagsDir, tagName))
        fos.write(value.getBytes)
        fos.close()
        Ok(JsString(""))

      case _ => BadRequest
    }
  }
}


