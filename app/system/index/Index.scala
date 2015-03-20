package system.index

import models.{Repository, Tag}
import play.api.libs.iteratee.Iteratee
import services.ContentEnumerator
import system.registry.ResourceType

import scala.concurrent.{ExecutionContext, Future}

trait Index {
  def repositories(implicit ctx: ExecutionContext): Future[Iterable[Repository]]

  def exists(repo: Repository)(implicit ctx: ExecutionContext): Future[Boolean]

  def create(repo:Repository)(implicit ctx: ExecutionContext): Future[Unit]

  def tagSet(repo: Repository)(implicit ctx: ExecutionContext): Future[Set[Tag]]

  def imagesStream(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]

  def tagsStream(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]]

  def tag(repo: Repository, tagName: String)(implicit ctx: ExecutionContext): Future[Option[String]]

  def writeTag(repo: Repository, tagName: String, value: String)(implicit ctx: ExecutionContext): Future[Unit]

  def sinkFor(repository: Repository, resourceType: ResourceType, option: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = ???

  def init(): Unit = {}
}
