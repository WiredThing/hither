package controllers

import fly.play.aws.{Aws4Signer, AwsCredentials}
import fly.play.s3.{BucketItem, S3, S3Client, S3Configuration}
import models.ImageId
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsArray
import play.api.libs.ws.{WS, WSProxyServer, WSRequestHolder}
import play.api.mvc.{Action, Controller, Flash}
import system.index.S3Index
import system.{Configuration, Production}

import scala.concurrent.Future
import scala.xml.Elem

case class MultipartUpload(key: String, uploadId: String)

class HitherS3Signer(credentials: AwsCredentials, region: String, proxy: Option[WSProxyServer])
  extends Aws4Signer(credentials, "s3", region) {

  private def addProxy(request: WSRequestHolder): WSRequestHolder = {
    proxy.map { p =>
      Logger.debug("Adding proxy to request")
      request.withProxyServer(p)
    }.getOrElse(request)
  }

  // allways include the content payload header
  override def sign(request: WSRequestHolder, method: String, body: Array[Byte]): WSRequestHolder =
    super.sign(addProxy(request.withHeaders(amzContentSha256(body))), method, body)

}

object S3Controller extends Controller with ContentFeeding {
  lazy val s3Config = S3Configuration.fromConfig
  lazy val s3Client = new S3Client(WS.client, new HitherS3Signer(s3Config.credentials, s3Config.region, Configuration.aws.proxy), s3Config)

  lazy val s3 = new S3(s3Client)

  lazy val bucket = s3.getBucket(Configuration.s3.bucketName)

  def multipartUploads = Action.async { implicit request =>
    val awsRequest = listMultipartUploads

    awsRequest.get().map { response =>
      response.status match {
        case 200 => Ok(views.html.listMultipartUploads(Configuration.s3.bucketName,
          extract(scala.xml.XML.loadString(response.body)).sortBy(_.uploadId)))

        case _ => InternalServerError
      }
    }
  }

  private def listMultipartUploads =
    s3Client.resourceRequest(Configuration.s3.bucketName, "")
      .withHeaders("Content-Type" -> "application/json")
      .withQueryString("uploads" -> "")


  private def removeMultipartUpload(upload: MultipartUpload): Future[String] = {
    val url = s"http://${Configuration.s3.bucketName}.${s3Config.host}/${upload.key}"

    val awsRequest = s3Client.resourceRequest(Configuration.s3.bucketName, upload.key)
      .withQueryString("uploadId" -> upload.uploadId)

    awsRequest.delete().map { response =>
      response.status match {
        case 204 => s"${upload.key} cleared"
        case s => s"${upload.key} not cleared with status $s, ${response.body}"
      }
    }
  }

  def clearMultipartUploads = Action.async { implicit request =>
    listMultipartUploads.get().flatMap { response =>
      response.status match {
        case 200 =>
          val fs = extract(scala.xml.XML.loadString(response.body)).map(removeMultipartUpload)
          Future.sequence(fs).map { results =>
            val flash = Flash(Map(results.map("success" -> _): _*))
            Redirect(routes.RepositoryController.repositories).flashing(flash)
          }
        case _ => Future(InternalServerError)
      }
    }
  }

  private def extract(xml: Elem): List[MultipartUpload] = {
    (xml \ "Upload").iterator.toList.map { node =>
      (node \ "Key", node \ "UploadId") match {
        case (key, uploadId) => MultipartUpload(key.text, uploadId.text)
      }
    }
  }

   def trimQuotes(s: String): String = s match {
    case _ if s.startsWith("\"") => trimQuotes(s.substring(1))
    case _ if s.startsWith("'") => trimQuotes(s.substring(1))
    case _ if s.endsWith("\"") => trimQuotes(s.substring(0, s.length - 1))
    case _ if s.endsWith("'") => trimQuotes(s.substring(0, s.length - 1))
    case _ => s
  }

  private def ancestryFor(imageId: ImageId): Future[Set[ImageId]] = {
    val registry = Production.s3Registry
    registry.ancestry(imageId).flatMap {
      case Some(ce) => ce.asJson.map {
        case JsArray(ids) => ids.map(id => ImageId(trimQuotes(id.toString()))).toSet
        case _ => Logger.debug(s"Ancestry for $imageId was not a JsArray!"); Set()
      }
      case None => Future(Set())
    }
  }

  private def listIndexedLayers: Future[Set[ImageId]] = {
    val index: S3Index = Production.s3Index
    val headImages = index.repositories.flatMap { repos =>
      repos.foldLeft(Future(Set[ImageId]())) { case (f, repo) =>
        f.flatMap { imageIds =>
          index.tagSet(repo).map { tags =>
            tags.map(_.version) ++ imageIds
          }
        }
      }
    }

    headImages.flatMap { imageIds =>
      imageIds.foldLeft(Future(Set[ImageId]())) { case (f, i) =>
        f.flatMap(acc => ancestryFor(i).map(_ ++ acc))
      }
    }
  }

  def removeOrphanedLayers() = Action.async { implicit request =>
    for {
      indexed <- listIndexedLayers
      all <- listAllLayers
      toRemove = all -- indexed
      _ <- removeLayers(toRemove)
    } yield {
      Redirect(routes.RepositoryController.repositories).flashing("success" -> s"Found ${indexed.size} layers in the index. ${all.size} layers in total. Removed ${toRemove.size}")
    }
  }

  private def listAllLayers: Future[Set[ImageId]] = {
    bucket.list(Configuration.s3.registryRoot + "/").map(_.map(item => ImageId(item.name.split("/").last)).toSet)
  }

  /**
   * Removes layer directories in the S3 bucket that do not have a 'layer' entry in them
   */
  def removeIncomplete() = Action.async { implicit request =>
    findIncompletes.map { incompletes =>
      incompletes.map(i => ImageId(i.name.split("/").last)).foreach(removeLayer)
      Redirect(routes.RepositoryController.repositories).flashing("success" -> s"Removed ${incompletes.length} incomplete layers from ${bucket.name}")
    }
  }

  private def removeLayers(layerIds: Set[ImageId]): Future[Unit] = {
    layerIds.foldLeft(Future[Unit](Unit)) { case (f, layerId) =>
      f.flatMap(_ => removeLayer(layerId))
    }
  }

  private def removeLayer(layerId: ImageId): Future[Unit] = {
    Logger.debug(s"Removing layer $layerId")
    val layerPath: String = Configuration.s3.registryRoot + "/" + layerId.id + "/"

    bucket.list(layerPath).map {
      _.foldLeft(Future[Unit](Unit)) { case (f, i) =>
        bucket.remove(i.name)
      }
    }.flatMap(_ => bucket.remove(layerPath))
  }

  private def findIncompletes: Future[List[BucketItem]] = {
    bucket.list(Configuration.s3.registryRoot + "/").flatMap { layerIds =>
      layerIds.foldLeft(Future[List[BucketItem]](List())) { case (f, layerItem) =>
        f.flatMap { acc =>
          bucket.list(layerItem.name).map { items =>
            val entries = items.map(_.name.split("/").last).toList
            if (!entries.contains("layer")) layerItem +: acc else acc
          }
        }
      }
    }
  }
}
