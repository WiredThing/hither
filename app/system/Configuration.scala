package system

import play.api.Play
import Play.current
import scala.util.Try
import java.io.File

object Configuration {

  lazy val registryRoot = Play.configuration.getString("registry.root").getOrElse("/tmp/registry")

  lazy val cacheRoot = Play.configuration.getString("registry.cache.root").getOrElse("/tmp/registry-cache")

  def buildCachePath(name: String): Try[File] = Try {
    val registryRoot = new File(system.Configuration.cacheRoot)
    registryRoot.mkdirs

    new File(registryRoot, name)
  }

}
