package system

import play.api.Play
import Play.current

object Configuration {

  lazy val registryRoot = Play.configuration.getString("registry.root").getOrElse("/tmp/registry")
  
  lazy val cacheRoot = Play.configuration.getString("registry.cache.root").getOrElse("/tmp/registry-cache")

}
