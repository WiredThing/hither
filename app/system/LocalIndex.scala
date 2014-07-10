package system

import java.io.File

import models.Repository
import play.api.Logger

trait LocalIndex extends Index {
  override def init(): Unit = {
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
