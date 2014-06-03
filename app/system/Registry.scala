package system

import java.io.File
import scala.io.Source
import models.ImageId

object Registry {

  def findLocalSource(imageId: ImageId, extension: Option[String] = None): Option[LocalSource] = {
    val name = List(Some(s"${imageId.id}"), extension).flatten.mkString(".")
    List(Registry.buildRegistryPath(name).existing, Registry.buildCachePath(name).existing).flatten.headOption
  }

  trait LocalSource {
    def file: File

    def source: String

    def mkdirs() = {file.mkdirs(); this}

    def exists() = file.exists()

    def getAbsolutePath() = file.getAbsolutePath()

    def length() = file.length()

    def asString() : String = {
      val s = Source.fromFile(file)
      val string = s.mkString
      s.close()
      string
    }

    def existing:Option[LocalSource] = if (exists()) Some(this) else None
  }

  case class RegistryFile(file: File) extends LocalSource {
    val source = "registry"
  }

  case class CacheFile(file: File) extends LocalSource {
    val source = "cache"
  }

  def buildRegistryPath(name: String): RegistryFile = {
    RegistryFile(new File(new File(system.Configuration.registryRoot), name))
  }

  def buildCachePath(name: String): CacheFile = {
    CacheFile(new File(new File(system.Configuration.cacheRoot), name))
  }
}
