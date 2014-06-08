package system

import java.io.File
import models.ImageId
import play.api.Logger

object LocalRegistry {

  def findLocalSource(imageId: ImageId, extension: String): Option[LocalSource] = {
    List(LocalRegistry.buildRegistryPath(s"${imageId.id}.$extension").existing, LocalRegistry.buildCachePath(s"${imageId.id}.$extension").existing).flatten.headOption
  }

  def createDirs: Unit = {
    Logger.info(s"Creating $registryRoot")
    registryRoot.mkdirs()
    Logger.info(s"Creating $cacheRoot")
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
