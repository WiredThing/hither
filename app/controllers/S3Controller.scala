package controllers

import fly.play.aws.Aws.AwsRequestHolder
import fly.play.s3.{BucketFile, S3}
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Action, Controller}
import system.Configuration

import scala.concurrent.Future
import scala.xml.Elem

case class MultipartUpload(key: String, uploadId: String)

object S3Controller extends Controller with ContentFeeding {
  lazy val s3 = S3.fromConfig

  lazy val bucket = s3.getBucket(Configuration.s3.bucketName)

  def multipartUploads = Action.async { request =>
    val awsRequest: AwsRequestHolder = listMultipartUploads

    awsRequest.get().map { response =>
      response.status match {
        case 200 => Ok(views.html.listMultipartUploads(Configuration.s3.bucketName,
          extract(scala.xml.XML.loadString(response.body)).sortBy(_.uploadId)))

        case _ => InternalServerError
      }
    }
  }

  def listMultipartUploads = {
    val url = "http://" + Configuration.s3.bucketName + "." + s3.host

    val awsRequest = s3.awsWithSigner
      .url(url)
      .withHeaders("Content-Type" -> "application/json")
      .withQueryString("uploads" -> "")
    awsRequest
  }

  def removeMultipartUpload(upload: MultipartUpload): Future[String] = {
    val url = s"http://${Configuration.s3.bucketName}.${s3.host}/${upload.key}"

    val awsRequest = s3.awsWithSigner
      .url(url)
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
