package controllers

import play.api.mvc.{Result, Action, Controller}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import models.ImageId
import play.api.Logger
import system.Registry
import scala.concurrent.Future
import play.api.libs.json.{Json, JsString}
import scala.Some
import play.api.libs.iteratee.{Iteratee, Enumerator}
import java.io.FileOutputStream
import system.Registry.RegistryFile

object NotFoundException extends Exception

object Images extends Controller {
  def ancestry(imageId: ImageId) = Action.async { implicit request =>
    buildAncestry(imageId).map { l => Ok(Json.toJson(l))}
  }

  def buildAncestry(imageId: ImageId): Future[List[String]] = {
    Logger.info(s"Building ancestry for ${imageId.id}")
    Registry.findLocalSource(imageId, Some("ancestry")) match {
      case Some(localAncestry) => {
        Logger.info(s"Serving ancestry from ${localAncestry.getAbsolutePath()}")
        Future(Json.parse(localAncestry.source.mkString).as[List[String]])
      }
      case None => Registry.findLocalSource(imageId, Some("json")) match {
        case Some(r: RegistryFile) => Json.parse(r.source.mkString) \ "parent" match {
          case JsString(id) => buildAncestry(ImageId(id)).map(imageId.id +: _)
          case _ => Future(List())
        }
        case _ => findData(imageId, "ancestry").flatMap { t =>
          val (e, _, _) = t

          e.run(Iteratee.getChunks).map { byteArrays =>
            val s = byteArrays.map(new String(_)).mkString
            Json.parse(s).as[List[String]]
          }
        }
      }
    }
  }

  def json(imageId: ImageId) = Action.async { implicit request =>
    findData(imageId, "json").map(feedResult)
  }

  def layer(imageId: ImageId) = Action.async { implicit request =>
    findData(imageId, "layer", "binary/octet-stream").map(feedResult)
  }

  def findData(imageId: ImageId, extension: String, contentType: String = "application/json"): Future[(Enumerator[Array[Byte]], String, Option[String])] = {
    val result = Registry.findLocalSource(imageId, Some(extension)) match {
      case Some(localSource) =>
        Logger.info(s"Supplying $extension for ${imageId.id} from ${localSource.kind}")
        Future((Enumerator.fromFile(localSource.file), contentType, Some(localSource.length().toString)))

      case None =>
        Logger.info(s"Going to docker for ${imageId.id}/$extension")
        respondFromUrl(s"${imageId.id}.$extension", s"http://registry-1.docker.io/v1/images/${imageId.id}/$extension")
    }
    result
  }

  def feedResult(result: (Enumerator[Array[Byte]], String, Option[String])): Result = {
    result match {
      case (e, contentType, Some(length)) => Ok.feed(e).as(contentType).withHeaders("Content-Length" -> length)
      case (e, contentType, None) => Ok.chunked(e).as(contentType)
    }
  }

  def putJson(imageId: ImageId) = Action(parse.file(Registry.buildRegistryPath(s"${imageId.id}.json").file)) {
    request =>
      Logger.info(s"Layer json pushed to ${request.body.getAbsolutePath}")
      Ok(JsString(""))
  }


  def putLayer(imageId: ImageId) = Action(parse.file(Registry.buildRegistryPath(s"${imageId.id}.layer").file)) {
    request =>
      Logger.info(s"Layer pushed to ${request.body.getAbsolutePath}")
      Ok(JsString(""))
  }

  def putChecksum(imageId: ImageId) = Action(parse.file(Registry.buildRegistryPath(s"${
    imageId.id
  }.checksum").file)) {
    request =>
      Logger.info(s"Checksum pushed to ${request.body.getAbsolutePath}")
      Ok(JsString(""))
  }

  def respondFromUrl(cacheFileName: String, url: String): Future[(Enumerator[Array[Byte]], String, Option[String])] = {
    WS.url(url).getStream().map {
      case (response, body) =>

        // Check that the response was successful
        if (response.status == 200) {
          // Get the content type
          val contentType = response.headers.get("Content-Type").flatMap(_.headOption)
            .getOrElse("binary/octet-stream")

          val cacheFile = Registry.buildCachePath(cacheFileName)
          val os = new FileOutputStream(cacheFile.file)

          val fileWriter: Enumerator[Array[Byte]] = body.map {
            bytes =>
              os.write(bytes)
              bytes
          }

          fileWriter.onDoneEnumerating(os.close())

          // If there's a content length, send that, otherwise return the body chunked
          response.headers.get("Content-Length") match {
            case Some(Seq(length)) => (fileWriter, contentType, Some(length))

            case _ => (fileWriter, contentType, None)

          }
        } else {
          throw NotFoundException
        }
    }
  }
}
