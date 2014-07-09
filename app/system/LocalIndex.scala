package system

import java.io.File

import models.Repository
import play.api.{Application, Logger}

object ProductionIndex extends S3Index {
  import fly.play.s3.S3
  override def bucketName: String = Configuration.s3.bucketName

  override implicit def app: Application = play.api.Play.current

  override lazy val s3: S3 = S3.fromConfig
}

trait LocalIndex extends Index {

  def repos: List[Repository] = ???

  def init(): Unit = {
    Logger.info(s"Creating $root")
    root.mkdirs()
  }

  def root = new File(system.Configuration.file.indexRoot)

  case class ImagesSource(file: File) extends FileLocalSource {
    val kind = "images"
  }

  case class TagSource(file: File, tagName: String) extends FileLocalSource {
    val kind = tagName
  }

  def buildImagesPath(repo: Repository) = {
    ImagesSource(new File(buildRepoDir(repo), "images"))
  }

  def buildTagPath(repo: Repository, tagName: String) = {
    TagSource(new File(buildTagsDir(repo), tagName), tagName)
  }

  def buildTagsDir(repo: Repository) = {
    new File(buildRepoDir(repo), "tags")
  }

  def createRepoDir(repo: Repository) = buildRepoDir(repo).mkdirs()

  def buildRepoDir(repo: Repository): File = {
    val name = s"${repo.namespace.name}/${repo.repoName.name}"
    new File(root, name)
  }

}
