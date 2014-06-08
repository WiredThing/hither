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
  def tags(repo: Repository) = Action.async { implicit request =>
    val tagsDir = LocalIndex.buildTagsDir(repo)
    Logger.info("Tags dir is " + tagsDir.getAbsolutePath)
    if (tagsDir.exists()) {
      feedTagsFromLocal(tagsDir)
    } else {
      TagService.getTags(repo).map(Ok(_))
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

  def tagName(repo: Repository, tagName: String) = Action.async { implicit request =>
    TagService.getTag(repo, tagName).map(Ok(_))
  }

  def putTagName(repo: Repository, tagName: String) = {
    Action(parse.json) { request =>
      writeTagsfile(repo, tagName, request)
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


