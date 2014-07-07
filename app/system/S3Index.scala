package system

import fly.play.s3.{BucketFile, S3}
import models.Repository
import play.api.Logger
import play.api.libs.iteratee.Iteratee
import system.registry.ResourceType

import scala.concurrent.ExecutionContext

trait S3Index extends Index {
  def bucketName: String

  def s3: S3

  implicit def app: play.api.Application

  override def sinkFor(repo: Repository, resourceType: ResourceType, contentLength: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    Logger.info(s"Creating a sink for ${repo.qualifiedName} for resource $resourceType")

    Iteratee.consume[Array[Byte]]().map { bytes =>
      Logger.info(s"Consumed ${bytes.length} bytes of data")
      val bucket = s3.getBucket(bucketName)
      val fileName = s"${Configuration.indexRoot}/${repo.qualifiedName}/${resourceType.name}"
      Logger.info(s"Sending to bucketFile with name $fileName")
      val bucketFile = BucketFile(fileName, resourceType.contentType, bytes)

      bucket.add(bucketFile)
    }
  }

  def init(): Unit = {}
}
