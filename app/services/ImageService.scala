package services


import java.io.{ByteArrayInputStream, File}

import models.ImageId
import play.api.LoggerLike
import play.api.Play.current
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.{Reads, JsValue, JsString, Json}
import system._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class ContentEnumerator(content: Enumerator[Array[Byte]], contentType: String, contentLength: Option[Long]) {
  def asString(implicit ctx: ExecutionContext): Future[String] =
    content.run(Iteratee.getChunks).map(_.map(new String(_)).mkString)

  def asJson(implicit ctx: ExecutionContext): Future[JsValue] = asString.map(Json.parse)

  def parseJson[A](implicit ctx: ExecutionContext, reads:Reads[A]): Future[A] = asJson.map(_.as[A])
}

object ContentEnumerator {
  def apply(js: JsValue)(implicit ctx: ExecutionContext): ContentEnumerator = {
    val bytes = Json.stringify(js).getBytes()
    val e = Enumerator.fromStream(new ByteArrayInputStream(bytes))

    ContentEnumerator(e, "application/json", Some(bytes.length))
  }
}

object ProductionImageService extends ImageService {

  import java.io.{File, FileOutputStream}

  import scala.concurrent.ExecutionContext.Implicits.global

  import play.api.libs.ws.WS

  override def registryHostName = Configuration.registryHostName

  override def logger: LoggerLike = play.api.Logger

  override val localRegistry: LocalRegistry = ProductionLocalRegistry

  object AsLong {
    def unapply(s: String): Option[Long] = Try(s.toLong).toOption
  }

  def teeToFile(file: File)(e: Enumerator[Array[Byte]])(implicit ctx: ExecutionContext): Enumerator[Array[Byte]] = {
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

  def registryHostName: String

  import localRegistry.RegistryFile
  import system.ResourceType._

  import scala.concurrent.ExecutionContext.Implicits._

  protected def respondFromUrl(cacheFileName: String, url: String): Future[ContentEnumerator]

  def fileFor(imageId: ImageId, dataType: ResourceType): File = localRegistry.buildRegistryPath(s"${imageId.id}.${dataType.name}").file

  def findData(imageId: ImageId, dataType: ResourceType, contentType: String = "application/json")(implicit ctx: ExecutionContext): Future[ContentEnumerator] = {
    val result = localRegistry.findLocalSource(imageId, dataType) match {
      case Some(localSource) =>
        logger.info(s"Supplying ${dataType.name} for ${imageId.id} from ${localSource}")
        Future(ContentEnumerator(localSource.enumerator, contentType, Some(localSource.length())))

      case None =>
        logger.info(s"Going to docker for ${imageId.id}/${dataType.name}")
        respondFromUrl(s"${imageId.id}.${dataType.name}", s"http://$registryHostName/v1/images/${imageId.id}/${dataType.name}")
    }
    result
  }

  type Ancestry = List[String]

  def lookupAncestry(imageId: ImageId): Future[Ancestry] = {
    logger.info(s"Building ancestry for ${imageId.id}")

    localRegistry.findLocalSource(imageId, AncestryType) match {
      case Some(localAncestry) => serveAncestryFromLocal(localAncestry)
      case None => constructAncestry(imageId, lookupAncestry)
    }
  }

  private[services] def serveAncestryFromLocal(localAncestry: LocalSource)(implicit ctx: ExecutionContext): Future[Ancestry] = {
    logger.info(s"Serving ancestry from ${localAncestry.getAbsolutePath()}")
    Future(Json.parse(localAncestry.source.mkString).as[Ancestry])
  }

  private[services] def constructAncestry(imageId: ImageId, ancestryFinder: ImageId => Future[Ancestry]): Future[Ancestry] = {
    localRegistry.findLocalSource(imageId, JsonType) match {
      case Some(r: RegistryFile) => Json.parse(r.source.mkString) \ "parent" match {
        case JsString(parentId) => ancestryFinder(ImageId(parentId)).map(imageId.id +: _)
        case _ => Future(List(imageId.id))
      }

      case _ => findData(imageId, AncestryType).flatMap { ce =>
        ce.content.run(Iteratee.getChunks).map { byteArrays =>
          val s = byteArrays.map(new String(_)).mkString
          Json.parse(s).as[Ancestry]
        }
      }
    }
  }
}
