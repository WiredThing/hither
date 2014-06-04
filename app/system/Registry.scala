package system

import java.io.File
import models.ImageId

object Registry {

  def findLocalSource(imageId: ImageId, extension: Option[String] = None): Option[LocalSource] = {
    val name = List(Some(s"${imageId.id}"), extension).flatten.mkString(".")
    List(Registry.buildRegistryPath(name).existing, Registry.buildCachePath(name).existing).flatten.headOption
  }

  def createDirs: Unit = {
    registryRoot.mkdirs()
    cacheRoot.mkdirs()
  }


  def cacheRoot: File = new File(system.Configuration.cacheRoot)

  def registryRoot: File = new File(system.Configuration.registryRoot)

  case class RegistryFile(file: File) extends LocalSource {
    val kind = "registry"
  }

  case class CacheFile(file: File) extends LocalSource {
    val kind = "cache"
  }

  def buildRegistryPath(name: String): RegistryFile = RegistryFile(new File(registryRoot, name))

  def buildCachePath(name: String): CacheFile = CacheFile(new File(cacheRoot, name))
}
