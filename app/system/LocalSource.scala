package system

import java.io.File
import scala.io.Source

trait LocalSource {
  def file: File

  def kind: String

  def source = Source.fromFile(file)

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
