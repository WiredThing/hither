package controllers

import fly.play.s3.S3
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Action, Controller}
import system.Configuration

import scala.xml.Elem

case class MultipartUpload(key: String, uploadId: String)

object S3Controller extends Controller with ContentFeeding {
  lazy val s3 = S3.fromConfig

  def multipartUploads = Action.async { request =>

    val url = "http://" + Configuration.s3.bucketName + "." + s3.host

    val awsRequest = s3.awsWithSigner
      .url(url)
      .withHeaders("Content-Type" -> "application/json")
      .withQueryString("uploads" -> "")

    awsRequest.get.map { response =>
      response.status match {
        case 200 => {
          Ok(views.html.listMultipartUploads(Configuration.s3.bucketName, extract(scala.xml.XML.loadString(response.body))))
        }

        case _ => InternalServerError
      }
    }
  }

  def extract(xml: Elem): List[MultipartUpload] = {
    (xml \ "Upload").iterator.toList.map { node =>
      (node \ "Key", node \ "UploadId") match {
        case (key, uploadId) =>MultipartUpload(key.text, uploadId.text)
      }
    }
  }
}
