package system

import models.{Namespace, RepositoryName, Repository}
import java.io.File

object Index {

  def createDirs() : Unit = {
    root.mkdirs()
  }

  def root = new File(system.Configuration.indexRoot)

  case class ImagesSource(file: File) extends LocalSource {
    val kind = "images"
  }

  case class TagSource(file: File, tagName: String) extends LocalSource {
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

  def createRepoDir(repo:Repository) = buildRepoDir(repo).mkdirs()

  def buildRepoDir(repo: Repository): File = {
    val name =  s"${repo.namespace.name}/${repo.repoName.name}"
    new File(root, name)
  }

}
