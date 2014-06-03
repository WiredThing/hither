package services

import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsResult
import play.api.libs.ws.WS

import models._

case class ServiceResult[T](images: JsResult[T], headers: List[(String, String)])

object RegistryService {
  val headersToCopy = List("x-docker-token", "date", "Connection")

  def ancestry(imageId: ImageId): Future[ServiceResult[List[ImageId]]] = {
    WS.url(s"http://registry-1.docker.io/v1/images/${imageId.id}/ancestry").get().map { response =>
      val responseHeaders = headersToCopy.map { key => response.header(key).map((key, _))}.flatten
      val imageIds = response.json.validate[List[String]].map { ids => ids.map(ImageId(_))}
      ServiceResult(imageIds, responseHeaders)
    }
  }

  def json(imageId: ImageId): Future[ServiceResult[LayerDescriptor]] = {
    WS.url(s"http://registry-1.docker.io/v1/images/${imageId.id}/json").get().map { response =>
      val responseHeaders = headersToCopy.map { key => response.header(key).map((key, _))}.flatten

      val v = response.json.validate[LayerLink].map(layerLink => LayerDescriptor(layerLink, response.json))
      ServiceResult(v, responseHeaders)
    }
  }
}
