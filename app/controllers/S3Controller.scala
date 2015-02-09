package controllers

import fly.play.aws.{Aws4Signer, AwsCredentials}
import fly.play.s3.{S3, S3Client, S3Configuration}
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.{WS, WSProxyServer, WSRequestHolder}
import play.api.mvc.{Action, Controller}
import system.Configuration

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

  def multipartUploads = Action.async { request =>
    val awsRequest = listMultipartUploads

    awsRequest.get().map { response =>
      response.status match {
        case 200 => Ok(views.html.listMultipartUploads(Configuration.s3.bucketName,
          extract(scala.xml.XML.loadString(response.body)).sortBy(_.uploadId)))

        case _ => InternalServerError
      }
    }
  }

  def listMultipartUploads =
    s3Client.resourceRequest(Configuration.s3.bucketName, "")
      .withHeaders("Content-Type" -> "application/json")
      .withQueryString("uploads" -> "")


  def removeMultipartUpload(upload: MultipartUpload): Future[String] = {
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

  def clearMultipartUploads = Action.async { request =>
    listMultipartUploads.get().flatMap { response =>
      response.status match {
        case 200 =>
          val fs = extract(scala.xml.XML.loadString(response.body)).map(removeMultipartUpload)
          Future.sequence(fs).map(results => Ok(results.mkString("\n")))
        case _ => Future(InternalServerError)
      }
    }
  }

  def extract(xml: Elem): List[MultipartUpload] = {
    (xml \ "Upload").iterator.toList.map { node =>
      (node \ "Key", node \ "UploadId") match {
        case (key, uploadId) => MultipartUpload(key.text, uploadId.text)
      }
    }
  }

  def removeOrphans = Action.async { request =>
    bucket.list(Configuration.s3.registryRoot + "/").flatMap { layerIds =>
      val orphanedLayers = layerIds.toList.map { layerId =>
        bucket.list(Configuration.s3.registryRoot + "/" + layerId + "/").map { items =>
          val entries = items.map(_.name.split("/").last).toList
          if (!entries.contains("layer")) Some(layerId) else None
        }
      }

      Future.sequence(orphanedLayers).map(_.flatten.map(_.name)).map { orphans =>
        orphans.foreach(layerId => bucket.remove(Configuration.s3.registryRoot + "/" + layerId + "/"))
        Ok(views.html.listRemovedOrphans(bucket.name, orphans))
      }
    }
  }
}
