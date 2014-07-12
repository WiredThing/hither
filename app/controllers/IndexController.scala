package controllers

import play.api.LoggerLike
import play.api.mvc.Controller


object IndexController extends IndexController {

  import system.{Index, Production}

  override lazy val index: Index = Production.index

  override lazy val logger: LoggerLike = play.api.Logger
}

trait IndexController extends Controller with ContentFeeding {

  import models.Repository
  import play.api.libs.concurrent.Execution.Implicits._
  import play.api.libs.json.JsString
  import play.api.mvc.{Action, BodyParser}
  import system.registry.ResourceType
  import system.{Index, IndexTypes}

  import scala.concurrent.Future


  def index: Index

  def logger: LoggerLike

  def images(repo: Repository) = Action.async { implicit request =>
    logger.debug("images")
    index.images(repo).map {
      case Some(ce) => feedContent(ce)
      case None => NotFound
    }
  }

  def putImages(repo: Repository) = Action(toIndex(repo, IndexTypes.ImagesType, index)) { request =>
    // Don't need to do anything - all taken care of by the BodyParser. Docker expects NoContent
    // as a response.
    NoContent
  }

  def tags(repo: Repository) = Action.async { implicit request =>
    logger.debug("tags")
    index.tags(repo).map {
      case Some(ce) => feedContent(ce)
      case None => NotFound
    }
  }

  def tagName(repo: Repository, tagName: String) = Action.async { implicit request =>
    logger.debug("tagName")
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
    import scala.util.Try

    BodyParser("to index") { request =>
      logger.info(s"toIndex for ${repo.qualifiedName}")
      val contentLength = request.headers.get("Content-Length").flatMap(s => Try(s.toLong).toOption)
      index.sinkFor(repo, resourceType, contentLength).map { _ => Right(Unit)}
    }
  }

  def allocateRepo(repo: Repository) = Action(parse.json) { request =>
    Ok.withHeaders(
      ("X-Docker-Token", s"""signature=123abc,repository="${repo.qualifiedName}",access=write"""),
      ("X-Docker-Endpoints", request.headers("Host"))
    )
  }
}
