package system

import fly.play.s3.{BucketFile, S3}
import models.Repository
import play.api.libs.iteratee.Iteratee
import system.registry.ResourceType

import scala.concurrent.ExecutionContext

trait S3Index extends Index {
  def bucketName: String

  def s3: S3

  implicit def app: play.api.Application

  override def sinkFor(repo: Repository, resourceType: ResourceType, contentLength: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    Iteratee.consume[Array[Byte]]().map { bytes =>
      val bucket = s3.getBucket(bucketName)
      val fileName = s"${Configuration.indexRoot}/${repo.qualifiedName}/${resourceType.name}"
      val bucketFile = BucketFile(fileName, resourceType.contentType, bytes)

      bucket.add(bucketFile)
    }
  }

  def init(): Unit = {}
}
