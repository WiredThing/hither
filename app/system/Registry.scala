package system

import java.io.File
import scala.io.Source
import models.ImageId

object Registry {

  def findLocalSource(imageId: ImageId, extension: Option[String] = None): Option[LocalSource] = {
    val name = List(Some(s"${imageId.id}"), extension).flatten.mkString(".")
    List(Registry.buildRegistryPath(name).existing, Registry.buildCachePath(name).existing).flatten.headOption
  }


  case class RegistryFile(file: File) extends LocalSource {
    val kind = "registry"
  }

  case class CacheFile(file: File) extends LocalSource {
    val kind = "cache"
  }

  def buildRegistryPath(name: String): RegistryFile = {
    RegistryFile(new File(new File(system.Configuration.registryRoot), name))
  }

  def buildCachePath(name: String): CacheFile = {
    CacheFile(new File(new File(system.Configuration.cacheRoot), name))
  }
}
