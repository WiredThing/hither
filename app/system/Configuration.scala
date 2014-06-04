package system

import play.api.Play
import Play.current
import java.io.File
import models.{Namespace, RepositoryName, Repository}

object Configuration {

  lazy val indexRoot = Play.configuration.getString("registry.index").getOrElse("/tmp/index")

  lazy val registryRoot = Play.configuration.getString("registry.root").getOrElse("/tmp/registry")

  lazy val cacheRoot = Play.configuration.getString("registry.cache.root").getOrElse("/tmp/registry-cache")


  def buildRepoIndexPath(repo: Repository): File =  {
    val indexRoot = new File(system.Configuration.indexRoot)

    val path =  new File(new File(indexRoot, repo.namespace.name), repo.repoName.name)

    path.mkdirs()

    path
  }

}
