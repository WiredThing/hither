package system

import java.io.{OutputStream, FileOutputStream, File}
import play.api.libs.iteratee.Enumerator

import scala.concurrent.ExecutionContext
import scala.io.Source

trait LocalSource {

  def source: Source

  def enumerator(implicit ctx: ExecutionContext): Enumerator[Array[Byte]]

  def outputStream: OutputStream = ???

  def mkdirs(): LocalSource

  def exists(): Boolean

  def getAbsolutePath()

  def length(): Long

  def asString(): String

  def existing: Option[LocalSource] = if (exists()) Some(this) else None
}

trait FileLocalSource extends LocalSource {
  def file: File


  override def length(): Long = file.length()

  override def source: Source = Source.fromFile(file)


  override def outputStream: OutputStream = {
    new FileOutputStream(file)
  }

  override def mkdirs(): LocalSource = {
    file.mkdirs()
    this
  }

  override def getAbsolutePath(): Unit = file.getAbsolutePath

  override def enumerator(implicit ctx: ExecutionContext): Enumerator[Array[Byte]] = Enumerator.fromFile(file)

  def asString(): String = {
    val s = Source.fromFile(file)
    val string = s.mkString
    s.close()
    string
  }

  override def exists(): Boolean = file.exists()
}
