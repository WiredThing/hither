package services

import scala.concurrent.Future

import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{Json, JsResult}
import play.api.libs.ws.WS

import models._
import system._

trait ServiceResult[T]

case class NotFoundResult[T]() extends ServiceResult[T]

case class ErrorResult[T](code: Int) extends ServiceResult[T]

case class JsonResult[T](images: JsResult[T], headers: List[(String, String)]) extends ServiceResult[T]

object RegistryService extends RegistryService {
  override def registryHostName = Configuration.registryHostName

  override lazy val localRegistry = ProductionLocalRegistry
}

trait RegistryService {
  val headersToCopy = List("x-docker-token", "date", "Connection")

  def registryHostName: String

  val localRegistry: LocalRegistry

  def ancestry(imageId: ImageId): Future[ServiceResult[List[ImageId]]] = {
    WS.url(s"http://$registryHostName/v1/images/${imageId.id}/ancestry").get().map { response =>
      val responseHeaders = headersToCopy.map { key => response.header(key).map((key, _))}.flatten

      response.status match {
        case ok if ok >= 200 && ok <= 299 =>
          val imageIds = response.json.validate[List[String]].map { ids => ids.map(ImageId(_))}
          JsonResult(imageIds, responseHeaders)

        case 404 => NotFoundResult()

        case code => ErrorResult(code)
      }

    }
  }

  def json(imageId: ImageId): Future[ServiceResult[LayerDescriptor]] = {
    localRegistry.findLocalSource(imageId, ResourceType.JsonType) match {
      case Some(localSource) => Logger.info(s"Serving json from local file ${localSource.getAbsolutePath}")
        processJsonFile(localSource)

      case None =>
        WS.url(s"http://$registryHostName/v1/images/${imageId.id}/json").get().map { response =>
          val responseHeaders = headersToCopy.map { key => response.header(key).map((key, _))}.flatten

          response.status match {
            case ok if ok >= 200 && ok <= 299 =>
              val v = response.json.validate[LayerLink].map(layerLink => LayerDescriptor(layerLink, response.json))
              JsonResult(v, responseHeaders)

            case 404 => NotFoundResult()

            case code => ErrorResult(code)
          }
        }
    }
  }


  def processJsonFile(source: LocalSource): Future[JsonResult[LayerDescriptor]] = {
    Future {
      val json = Json.parse(source.asString())
      val v = json.validate[LayerLink].map(layerLink => LayerDescriptor(layerLink, json))
      JsonResult(v, List())
    }
  }
}
