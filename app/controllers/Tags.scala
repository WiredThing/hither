package controllers

import models.Repository
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{Action, Controller}
import services.TagService
import system.{LocalIndex, ProductionLocalIndex}

import scala.concurrent.Future

object Tags extends Tags {
  lazy val tagService = TagService
  lazy val localIndex = ProductionLocalIndex
}

trait Tags extends Controller {

  def tagService: TagService

  def localIndex: LocalIndex

  def tags(repo: Repository) = Action.async { implicit request =>
    val tagsDir = localIndex.buildTagsDir(repo)
    Logger.info("Tags dir is " + tagsDir.getAbsolutePath)
    if (tagsDir.exists()) {
      TagService.feedTagsFromLocal(tagsDir).map(tags => Ok(Json.toJson(tags)))
    } else {
      TagService.getTags(repo).map(Ok(_))
    }
  }

  def tagName(repo: Repository, tagName: String) = Action.async { implicit request =>
    TagService.getTag(repo, tagName).map(Ok(_))
  }

  def putTagName(repo: Repository, tagName: String) = {
    Action.async(parse.json) { request =>
      request.body match {
        case JsString(value) => TagService.writeTagsfile(repo, tagName, value).map(_ => Ok(JsString("")))
        case _ => Future.successful(BadRequest)
      }
    }
  }
}


