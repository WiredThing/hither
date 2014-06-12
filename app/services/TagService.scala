package services

import models._
import scala.concurrent.Future

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import play.api.libs.json.JsValue
import java.io.{FileOutputStream, FileFilter, File}
import scala.io.Source
import play.api.Logger
import system.{ProductionLocalIndex, Configuration, LocalIndex}

object TagService extends TagService {
  override def registryHostName: String = Configuration.registryHostName

  override def localIndex = ProductionLocalIndex
}

trait TagService {

  def registryHostName: String

  def localIndex: LocalIndex

  def getTag(repo: Repository, tagName: String): Future[JsValue] = {
    WS.url(s"http://$registryHostName/v1/repositories/${repo.qualifiedName}/tags/$tagName").get().map {
      response => response.json
    }
  }

  def getTags(repo: Repository): Future[JsValue] = {
    WS.url(s"http://$registryHostName/v1/repositories/${repo.qualifiedName}/tags").get().map {
      response => response.json
    }
  }

  def feedTagsFromLocal(tagsDir: File): Future[Map[String, String]] = Future {
    val filter = new FileFilter {
      override def accept(f: File): Boolean = f.isFile
    }

    val tags = tagsDir.listFiles(filter).map { tagFile =>
      (tagFile.getName(), Source.fromFile(tagFile).mkString)
    }

    Map(tags: _*)
  }

  def writeTagsfile(repository: Repository, tagName: String, tagValue: String): Future[Unit] = Future {
    Logger.info(s"tag value is $tagValue")
    val tagsDir = localIndex.buildTagsDir(repository)
    tagsDir.mkdirs()
    val fos = new FileOutputStream(new File(tagsDir, tagName))
    fos.write(tagValue.getBytes)
    fos.close()
  }
}
