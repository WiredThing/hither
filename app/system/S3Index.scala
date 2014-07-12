package system

import fly.play.s3.{BucketFile, S3}
import models.{Namespace, RepositoryName, Repository}
import play.api.Logger
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.Json
import services.ContentEnumerator
import system.registry.ResourceType

import scala.concurrent.{Future, ExecutionContext}

trait S3Index extends Index {
  def bucketName: String

  def s3: S3

  val bucket = s3.getBucket(bucketName)

  implicit def app: play.api.Application

  override def repositories(implicit ctx: ExecutionContext): Future[List[Repository]] = {
    val index: String = s"${Configuration.s3.indexRoot}"

    bucket.list(s"$index/").flatMap { nsItems =>
      val futures = nsItems.toList.map { nsItem =>
        bucket.list(s"${nsItem.name}").map { repoItems =>
          repoItems.toList.map { repoItem =>
            Repository(Namespace(nsItem.name.split("/").last), RepositoryName(repoItem.name.split("/").last))
          }
        }
      }
      Future.sequence(futures).map(_.flatten)
    }
  }

  override def images(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    bucket.get(s"${Configuration.s3.indexRoot}/${repo.qualifiedName}/images").map { bucketFile =>
      Some(ContentEnumerator(Enumerator(bucketFile.content), "application/json", Some(bucketFile.content.length)))
    }.recover {
      case t => Logger.debug("", t); None
    }
  }

  override def tags(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    val tagsDir = s"${Configuration.s3.indexRoot}/${repo.qualifiedName}/tags/"
    bucket.list(tagsDir).flatMap { items =>
      val tagEntries = items.toList.map { item =>
        bucket.get(item.name).map(bf => (bf.name.split("/").last, new String(bf.content)))
      }
      val tagMap = Future.sequence(tagEntries).map(e => Map(e: _*))

      val cef = tagMap.map { m =>
        val jsonBytes = Json.prettyPrint(Json.toJson(m)).getBytes()
        Some(ContentEnumerator(Enumerator(jsonBytes), "application/json", Some(jsonBytes.length)))
      }
      cef
    }.recover {
      case t => Logger.debug("", t); None
    }
  }


  override def tag(repo: Repository, tagName: String)(implicit ctx: ExecutionContext): Future[Option[String]] = {
    val itemName = s"${Configuration.s3.indexRoot}/${repo.qualifiedName}/tags/$tagName"
    Logger.debug(s"Looking for tag $itemName")
    bucket.get(itemName).map { bucketFile =>
      Some(new String(bucketFile.content))
    }.recover {
      case t => Logger.debug("", t); None
    }
  }

  override def writeTag(repo: Repository, tagName: String, value: String)(implicit ctx: ExecutionContext): Future[Unit] = {
    val fileName = s"${
      Configuration.s3.indexRoot
    }/${
      repo.qualifiedName
    }/tags/$tagName"
    val bucketFile = BucketFile(fileName, "application/json", value.getBytes)

    bucket.add(bucketFile)
  }

  override def sinkFor(repo: Repository, resourceType: ResourceType, contentLength: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    Logger.info(s"Creating a sink for ${repo.qualifiedName} for resource $resourceType")
    val fileName = s"${Configuration.s3.indexRoot}/${repo.qualifiedName}/${resourceType.name}"
    bucketUpload(fileName, resourceType)
  }

  def bucketUpload(fileName: String, resourceType: ResourceType)(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    Iteratee.consume[Array[Byte]]().map { bytes =>
      Logger.info(s"Consumed ${bytes.length} bytes of data")
      Logger.info(s"Sending to bucketFile with name $fileName")
      val bucketFile = BucketFile(fileName, resourceType.contentType, bytes)

      bucket.add(bucketFile)
    }
  }
}
