package controllers

import models.Repository
import play.api.libs.json.JsString
import play.api.{Logger, LoggerLike}
import play.api.mvc.{Action, BodyParser, Controller, Result}
import services.ContentEnumerator
import system.registry.ResourceType
import system.{IndexTypes, Index, ProductionIndex}

import scala.concurrent.Future
import scala.util.Try
import play.api.libs.concurrent.Execution.Implicits._

object IndexController extends IndexController {
  override lazy val index: Index = ProductionIndex

  override lazy val logger: LoggerLike = play.api.Logger
}

trait IndexController extends Controller {
  def index: Index

  def logger: LoggerLike

  def images(repo: Repository) = Action.async { implicit request =>
    Logger.debug("images")
    index.images(repo).map {
      case Some(ce) =>feedContent(ce)
      case None => NotFound
    }
  }

  def putImages(repo: Repository) = Action(toIndex(repo, IndexTypes.ImagesType, index)) { request =>
    // Don't need to do anything - all taken care of by the BodyParser
    NoContent
  }

  def tags(repo: Repository) = Action.async { implicit request =>
    Logger.debug("tags")
    index.tags(repo).map {
      case Some(ce) => feedContent(ce)
      case None => NotFound
    }
  }

  def tagName(repo: Repository, tagName: String) = Action.async { implicit request =>
    Logger.debug("tagName")
    index.tag(repo, tagName).map {
      case Some(t) => Ok(JsString(t))
      case None => NotFound
    }
  }

  def putTagName(repo: Repository, tagName: String) = {
    Action.async(parse.json) { request =>
      request.body match {
        case JsString(value) => index.writeTag(repo, tagName, value).map(_ => Ok(JsString("")))
        case _ => Future.successful(BadRequest)
      }
    }
  }

  protected def toIndex(repo: Repository, resourceType: ResourceType, index: Index): BodyParser[Unit] = {
    BodyParser("to index") { request =>
      Logger.info(s"toIndex for ${repo.qualifiedName}")
      val contentLength = request.headers.get("Content-Length").flatMap(s => Try(s.toLong).toOption)
      index.sinkFor(repo, resourceType, contentLength).map { _ => Right(Unit)}
    }
  }

  def allocateRepo(repo: Repository) = Action(parse.json) { request =>
    //    index.createRepoDir(repo)
    Ok.withHeaders(
      ("X-Docker-Token", s"""signature=123abc,repository="${repo.qualifiedName}",access=write"""),
      ("X-Docker-Endpoints", request.headers("Host"))
    )
  }

  protected def feedContent(content: ContentEnumerator): Result = {
    content match {
      case ContentEnumerator(e, contentType, Some(length)) => Ok.feed(e).as(contentType).withHeaders("Content-Length" -> length.toString)
      case ContentEnumerator(e, contentType, None) => Ok.chunked(e).as(contentType)
    }
  }
}
