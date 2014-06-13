package services

import java.io.ByteArrayInputStream

import models.ImageId
import org.scalatest.{FlatSpec, Matchers}
import play.api.LoggerLike
import play.api.libs.iteratee.{Enumerator, Iteratee}
import system.{ResourceType, LocalRegistry, LocalSource}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.io.Source

class ImageServiceTest extends FlatSpec with Matchers {
  "findData" should "return a ContentEnumerator from the local source if it exists" in {
    val fr = for {
      ce <- TestImageService.findData(ImageId("local"), ResourceType.JsonType, "content/type")
      byteArrays <- ce.content.run(Iteratee.getChunks)
    } yield byteArrays.map(new String(_)).mkString

    Await.result(fr, 2 seconds) shouldBe dummyLocalSource.testString
  }

  it should "return a ContentEnumerator from a url if no local source exists" in {
    val fr = for {
      ce <- TestImageService.findData(ImageId("url"), ResourceType.JsonType, "content/type")
      byteArrays <- ce.content.run(Iteratee.getChunks)
    } yield byteArrays.map(new String(_)).mkString

    Await.result(fr, 2 seconds) shouldBe TestImageService.urlTestString
  }

  val dummyLocalSource = new LocalSource {
    val testString = "test data"

    override def length(): Long = testString.length

    override def enumerator(implicit ctx: ExecutionContext): Enumerator[Array[Byte]] = Enumerator.fromStream(new ByteArrayInputStream(testString.getBytes()))

    override def source: Source = ???

    override def asString(): String = ???

    override def mkdirs(): LocalSource = ???

    override def getAbsolutePath(): Unit = ???

    override def exists(): Boolean = ???
  }

  val dummyLocalRegistry = new LocalRegistry {
    override def findLocalSource(imageId: ImageId, dataType: ResourceType): Option[LocalSource] = imageId match {
      case ImageId("local") => Some(dummyLocalSource)
      case _ => None
    }
  }

  object TestImageService extends ImageService {
    override val localRegistry: LocalRegistry = dummyLocalRegistry


    override def logger: LoggerLike = DummyLogger

    val urlTestString = "url data"

    override def respondFromUrl(cacheFileName: String, url: String): Future[ContentEnumerator] = {
      val e = Enumerator.fromStream(new ByteArrayInputStream(urlTestString.getBytes()))
      Future.successful(ContentEnumerator(e, "test", Some(urlTestString.length)))
    }

    override def registryHostName: String = ""
  }

}