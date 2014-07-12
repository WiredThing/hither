package system

import models.Repository
import play.api.libs.iteratee.Iteratee
import services.ContentEnumerator
import system.registry.ResourceType

import scala.concurrent.{Future, ExecutionContext}

object IndexTypes {
  val ImagesType = ResourceType("images", "application/json")
}

trait Index {
  def repositories(implicit ctx: ExecutionContext): Future[List[Repository]]

  def images(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]

  def tags(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]

  def tag(repo: Repository, tagName: String)(implicit ctx: ExecutionContext): Future[Option[String]]

  def writeTag(repo: Repository, tagName: String, value: String)(implicit ctx: ExecutionContext): Future[Unit]

  def sinkFor(repository: Repository, resourceType: ResourceType, option: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = ???

  def init(): Unit = {}
}
