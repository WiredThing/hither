package system

import play.api.Play
import Play.current
import scala.util.Try
import java.io.File
import models.{Namespace, RepositoryName, Repository}

object Configuration {


  lazy val indexRoot = Play.configuration.getString("registry.index").getOrElse("/tmp/index")

  lazy val registryRoot = Play.configuration.getString("registry.root").getOrElse("/tmp/registry")

  lazy val cacheRoot = Play.configuration.getString("registry.cache.root").getOrElse("/tmp/registry-cache")

  def buildRegistryPath(name: String): Try[File] = Try {
    val registryRoot = new File(system.Configuration.registryRoot)
    registryRoot.mkdirs

    new File(registryRoot, name)
  }

  def buildCachePath(name: String): Try[File] = Try {
    val registryRoot = new File(system.Configuration.cacheRoot)
    registryRoot.mkdirs

    new File(registryRoot, name)
  }

  def buildRepoIndexPath(repo: Repository): Try[File] = Try {
    val indexRoot = new File(system.Configuration.indexRoot)

    val path = repo match {
      case Repository(None, RepositoryName(repoName)) => new File(new File(indexRoot, "_none_"), repoName)
      case Repository(Some(Namespace(namespace)), RepositoryName(repoName)) => new File(new File(indexRoot, namespace), repoName)
    }

    path.mkdirs()

    path
  }

}