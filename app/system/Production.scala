package system

import models.Repository
import play.api.Application
import services.ContentEnumerator
import system.registry.{Registry, S3Registry}

import scala.concurrent.{ExecutionContext, Future}

object Production {

  lazy val s3Index = new S3Index {

    import fly.play.s3.S3

    override def bucketName: String = Configuration.s3.bucketName

    override implicit def app: Application = play.api.Play.current

    override lazy val s3: S3 = S3.fromConfig
  }

  lazy val fileIndex = new LocalIndex {
    override def images(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = ???

    override def tag(repo: Repository, tagName: String)(implicit ctx: ExecutionContext): Future[Option[String]] = ???

    override def writeTag(repo: Repository, tagName: String, value: String)(implicit ctx: ExecutionContext): Future[Unit] = ???

    override def tags(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = ???
  }

  lazy val index = Configuration.storage match {
    case "s3" => s3Index
    case "file" => fileIndex
  }

  lazy val s3Registry = new S3Registry {

    import fly.play.s3.S3

    override implicit def app = play.api.Play.current

    override val bucketName: String = Configuration.s3.bucketName
    override val s3 = S3.fromConfig
  }

  lazy val registry: Registry = Configuration.storage match {
    case "s3" => s3Registry
  }

}
