package system.index

import fly.play.s3.{BucketFile, S3}
import models._
import play.api.LoggerLike
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.Json
import services.ContentEnumerator
import system.Configuration
import system.registry.ResourceType

import scala.concurrent.{ExecutionContext, Future}

trait S3Index extends Index {
  def bucketName: String

  def s3: S3

  def logger: LoggerLike

  val bucket = s3.getBucket(bucketName)

  implicit def app: play.api.Application

  override def repositories(implicit ctx: ExecutionContext): Future[Iterable[Repository]] = {
    val index: String = s"${Configuration.s3.indexRoot}"

    bucket.list(s"$index/").flatMap { nsItems =>
      Future.sequence {
        nsItems.map { nsItem =>
          bucket.list(s"${nsItem.name}").map { repoItems =>
            repoItems.map { repoItem =>
              Repository(Namespace(nsItem.name.split("/").last), RepositoryName(repoItem.name.split("/").last))
            }
          }
        }
      }.map(_.flatten)
    }
  }

  override def exists(repo: Repository)(implicit ctx: ExecutionContext): Future[Boolean] = {
    repositories.map(_.exists(_ == repo))
  }

  override def create(repo: Repository)(implicit ctx: ExecutionContext): Future[Unit] = {
    val repoDir = s"${Configuration.s3.indexRoot}/${repo.qualifiedName}"
    val imagesFileName = s"$repoDir/images"
    val imagesFile = BucketFile(imagesFileName, "application/json", "{}".getBytes)

    exists(repo).flatMap {
      case false => bucket.add(imagesFile)
      case true => Future(Unit)
    }
  }

  override def tagSet(repo: Repository)(implicit ctx: ExecutionContext): Future[Set[Tag]] = {
    val repoDir = s"${Configuration.s3.indexRoot}/${repo.qualifiedName}"
    val tagsDir = s"$repoDir/tags/"

    logger.info(s"getting tags from $tagsDir")

    bucket.list(tagsDir).flatMap { items =>
      logger.info(s"got tags $items")
      Future.sequence {
        items.map { item =>
          bucket.get(item.name).map(bf => Tag(bf.name.split("/").last, ImageId(new String(bf.content))))
        }.toSet
      }
    }
  }

  override def imagesStream(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    bucket.get(s"${Configuration.s3.indexRoot}/${repo.qualifiedName}/images").map { bucketFile =>
      Some(ContentEnumerator(Enumerator(bucketFile.content), "application/json", Some(bucketFile.content.length)))
    }.recover {
      case t => logger.debug("", t); None
    }
  }

  override def tagsStream(repo: Repository)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    tagSet(repo).map { tags =>
      val tagMap = Map(tags.toSeq.map(t => t.name -> t.version): _*)
      val jsonBytes = Json.prettyPrint(Json.toJson(tagMap)).getBytes
      Some(ContentEnumerator(Enumerator(jsonBytes), "application/json", Some(jsonBytes.length)))
    }.recover {
      case t => logger.error("", t); None
    }
  }


  override def tag(repo: Repository, tagName: String)(implicit ctx: ExecutionContext): Future[Option[String]] = {
    val itemName = s"${Configuration.s3.indexRoot}/${repo.qualifiedName}/tags/$tagName"
    logger.debug(s"Looking for tag $itemName")

    bucket.get(itemName).map { bucketFile =>
      Some(new String(bucketFile.content))
    }.recover {
      case t => logger.debug("", t); None
    }
  }

  override def writeTag(repo: Repository, tagName: String, value: String)(implicit ctx: ExecutionContext): Future[Unit] = {
    val fileName = s"${Configuration.s3.indexRoot}/${repo.qualifiedName}/tags/$tagName"
    val bucketFile = BucketFile(fileName, "application/json", value.getBytes)

    bucket.add(bucketFile)
  }

  override def sinkFor(repo: Repository, resourceType: ResourceType, contentLength: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    logger.info(s"Creating a sink for ${repo.qualifiedName} for resource $resourceType")
    val fileName = s"${Configuration.s3.indexRoot}/${repo.qualifiedName}/${resourceType.name}"
    bucketUpload(fileName, resourceType)
  }

  private def bucketUpload(fileName: String, resourceType: ResourceType)(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    Iteratee.consume[Array[Byte]]().map { bytes =>
      logger.info(s"Consumed ${bytes.length} bytes of data")
      logger.info(s"Sending to bucketFile with name $fileName")
      val bucketFile = BucketFile(fileName, resourceType.contentType, bytes)

      bucket.add(bucketFile)
    }
  }
}
