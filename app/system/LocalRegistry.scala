package system

import java.io.File

import models.ImageId
import play.api.Logger

object ProductionLocalRegistry extends LocalRegistry

case class RegistryType(name: String, contentType:String)

object RegistryType {
  val AncestryType = RegistryType("ancestry", "application/json")
  val JsonType = RegistryType("json", "application/json")
  val LayerType = RegistryType("layer","binary/octet-stream")
  val ChecksumType = RegistryType("checksum", "application/json")
}

trait LocalRegistry {

  def findLocalSource(imageId: ImageId, dataType: RegistryType): Option[LocalSource] = {
    List(
      buildRegistryPath(s"${imageId.id}.${dataType.name}").existing,
      buildCachePath(s"${imageId.id}.${dataType.name}").existing
    ).flatten.headOption
  }

  def createDirs: Unit = {
    Logger.info(s"Creating $registryRoot")
    registryRoot.mkdirs()
    Logger.info(s"Creating $cacheRoot")
    cacheRoot.mkdirs()
  }


  def cacheRoot: File = new File(system.Configuration.cacheRoot)

  def registryRoot: File = new File(system.Configuration.registryRoot)

  case class RegistryFile(file: File) extends FileLocalSource

  case class CacheFile(file: File) extends FileLocalSource

  def buildRegistryPath(name: String): RegistryFile = RegistryFile(new File(registryRoot, name))

  def buildCachePath(name: String): CacheFile = CacheFile(new File(cacheRoot, name))
}
