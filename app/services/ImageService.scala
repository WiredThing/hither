package services


import scala.concurrent.Future
import scala.util.Try

import play.api.libs.iteratee.{Iteratee, Enumerator}
import play.api.LoggerLike

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

import models.ImageId
import system.{ProductionLocalRegistry, Configuration, LocalRegistry}
import play.api.libs.json.{JsString, Json}

case class ContentEnumerator(content: Enumerator[Array[Byte]], contentType: String, contentLength: Option[Long])

object ProductionImageService extends ImageService {

  import play.api.libs.ws.WS
  import java.io.{File, FileOutputStream}

  override def registryHostName = Configuration.registryHostName

  override def logger: LoggerLike = play.api.Logger

  override val localRegistry: LocalRegistry = ProductionLocalRegistry

  object AsLong {
    def unapply(s: String): Option[Long] = Try(s.toLong).toOption
  }

  def teeToFile(file: File)(e: Enumerator[Array[Byte]]): Enumerator[Array[Byte]] = {
    val os = new FileOutputStream(file)
    val fileWriter: Enumerator[Array[Byte]] = e.map { bytes =>
      os.write(bytes)
      bytes
    }

    fileWriter.onDoneEnumerating(os.close())
    fileWriter
  }

  def respondFromUrl(cacheFileName: String, url: String): Future[ContentEnumerator] = {
    WS.url(url).getStream().map {
      case (response, body) =>
        if (response.status == 200) {
          val contentType = response.headers.get("Content-Type").flatMap(_.headOption).getOrElse("binary/octet-stream")

          val cacheFile = localRegistry.buildCachePath(cacheFileName)
          val fileWriter = teeToFile(cacheFile.file)(body)

          response.headers.get("Content-Length") match {
            case Some(Seq(AsLong(length))) => ContentEnumerator(fileWriter, contentType, Some(length))
            case _ => ContentEnumerator(fileWriter, contentType, None)
          }
        } else {
          throw NotFoundException(url)
        }
    }
  }
}

trait ImageService {
  def logger: LoggerLike

  val localRegistry: LocalRegistry

  def registryHostName:String

  import localRegistry.RegistryFile

  def respondFromUrl(cacheFileName: String, url: String): Future[ContentEnumerator]

  def findData(imageId: ImageId, extension: String, contentType: String = "application/json"): Future[ContentEnumerator] = {
    val result = localRegistry.findLocalSource(imageId, extension) match {
      case Some(localSource) =>
        logger.info(s"Supplying $extension for ${imageId.id} from ${localSource.kind}")
        Future(ContentEnumerator(Enumerator.fromFile(localSource.file), contentType, Some(localSource.length())))

      case None =>
        logger.info(s"Going to docker for ${imageId.id}/$extension")
        respondFromUrl(s"${imageId.id}.$extension", s"http://$registryHostName/v1/images/${imageId.id}/$extension")
    }
    result
  }

  def buildAncestry(imageId: ImageId): Future[List[String]] = {
    logger.info(s"Building ancestry for ${imageId.id}")

    localRegistry.findLocalSource(imageId, "ancestry") match {
      case Some(localAncestry) => {
        logger.info(s"Serving ancestry from ${localAncestry.getAbsolutePath()}")
        Future(Json.parse(localAncestry.source.mkString).as[List[String]])
      }
      case None => localRegistry.findLocalSource(imageId, "json") match {
        case Some(r: RegistryFile) => Json.parse(r.source.mkString) \ "parent" match {
          case JsString(parentId) => buildAncestry(ImageId(parentId)).map(imageId.id +: _)
          case _ => Future(List())
        }
        case _ => findData(imageId, "ancestry").flatMap { ce =>
          val e = ce.content

          e.run(Iteratee.getChunks).map { byteArrays =>
            val s = byteArrays.map(new String(_)).mkString
            Json.parse(s).as[List[String]]
          }
        }
      }
    }
  }
}
