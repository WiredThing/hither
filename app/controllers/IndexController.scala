package controllers

import models.Repository
import play.api.{Logger, LoggerLike}
import play.api.mvc.{Action, BodyParser, Controller, Result}
import services.ContentEnumerator
import system.registry.ResourceType
import system.{IndexTypes, Index, ProductionIndex}

import scala.util.Try
import play.api.libs.concurrent.Execution.Implicits._

object IndexController extends IndexController {
  override lazy val index: Index = ProductionIndex

  override lazy val logger: LoggerLike = play.api.Logger
}

trait IndexController extends Controller {
  def index: Index


  def logger: LoggerLike

  def images(repo: Repository) = Action { implicit request =>
    NoContent
  }

  def putImages(repo: Repository) = Action(toIndex(repo, IndexTypes.ImagesType, index)) { request =>
    NoContent
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
