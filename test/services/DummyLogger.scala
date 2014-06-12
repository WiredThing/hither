package services

import org.slf4j.{Marker, Logger}
import play.api.LoggerLike

object DummyLogger extends LoggerLike {
  override val logger: Logger = DummySlf4jLogger
}

object DummySlf4jLogger extends Logger{
  override def getName: String = "dummy"

  override def warn(msg: String): Unit = ???

  override def warn(format: String, arg: scala.Any): Unit = ???

  override def warn(format: String, arguments: AnyRef*): Unit = ???

  override def warn(format: String, arg1: scala.Any, arg2: scala.Any): Unit = ???

  override def warn(msg: String, t: Throwable): Unit = ???

  override def warn(marker: Marker, msg: String): Unit = ???

  override def warn(marker: Marker, format: String, arg: scala.Any): Unit = ???

  override def warn(marker: Marker, format: String, arg1: scala.Any, arg2: scala.Any): Unit = ???

  override def warn(marker: Marker, format: String, arguments: AnyRef*): Unit = ???

  override def warn(marker: Marker, msg: String, t: Throwable): Unit = ???

  override def isErrorEnabled: Boolean = ???

  override def isErrorEnabled(marker: Marker): Boolean = ???

  override def isInfoEnabled: Boolean = false

  override def isInfoEnabled(marker: Marker): Boolean = ???

  override def isDebugEnabled: Boolean = ???

  override def isDebugEnabled(marker: Marker): Boolean = ???

  override def isTraceEnabled: Boolean = ???

  override def isTraceEnabled(marker: Marker): Boolean = ???

  override def error(msg: String): Unit = ???

  override def error(format: String, arg: scala.Any): Unit = ???

  override def error(format: String, arg1: scala.Any, arg2: scala.Any): Unit = ???

  override def error(format: String, arguments: AnyRef*): Unit = ???

  override def error(msg: String, t: Throwable): Unit = ???

  override def error(marker: Marker, msg: String): Unit = ???

  override def error(marker: Marker, format: String, arg: scala.Any): Unit = ???

  override def error(marker: Marker, format: String, arg1: scala.Any, arg2: scala.Any): Unit = ???

  override def error(marker: Marker, format: String, arguments: AnyRef*): Unit = ???

  override def error(marker: Marker, msg: String, t: Throwable): Unit = ???

  override def debug(msg: String): Unit = ???

  override def debug(format: String, arg: scala.Any): Unit = ???

  override def debug(format: String, arg1: scala.Any, arg2: scala.Any): Unit = ???

  override def debug(format: String, arguments: AnyRef*): Unit = ???

  override def debug(msg: String, t: Throwable): Unit = ???

  override def debug(marker: Marker, msg: String): Unit = ???

  override def debug(marker: Marker, format: String, arg: scala.Any): Unit = ???

  override def debug(marker: Marker, format: String, arg1: scala.Any, arg2: scala.Any): Unit = ???

  override def debug(marker: Marker, format: String, arguments: AnyRef*): Unit = ???

  override def debug(marker: Marker, msg: String, t: Throwable): Unit = ???

  override def isWarnEnabled: Boolean = ???

  override def isWarnEnabled(marker: Marker): Boolean = ???

  override def trace(msg: String): Unit = ???

  override def trace(format: String, arg: scala.Any): Unit = ???

  override def trace(format: String, arg1: scala.Any, arg2: scala.Any): Unit = ???

  override def trace(format: String, arguments: AnyRef*): Unit = ???

  override def trace(msg: String, t: Throwable): Unit = ???

  override def trace(marker: Marker, msg: String): Unit = ???

  override def trace(marker: Marker, format: String, arg: scala.Any): Unit = ???

  override def trace(marker: Marker, format: String, arg1: scala.Any, arg2: scala.Any): Unit = ???

  override def trace(marker: Marker, format: String, argArray: AnyRef*): Unit = ???

  override def trace(marker: Marker, msg: String, t: Throwable): Unit = ???

  override def info(msg: String): Unit = ???

  override def info(format: String, arg: scala.Any): Unit = ???

  override def info(format: String, arg1: scala.Any, arg2: scala.Any): Unit = ???

  override def info(format: String, arguments: AnyRef*): Unit = ???

  override def info(msg: String, t: Throwable): Unit = ???

  override def info(marker: Marker, msg: String): Unit = ???

  override def info(marker: Marker, format: String, arg: scala.Any): Unit = ???

  override def info(marker: Marker, format: String, arg1: scala.Any, arg2: scala.Any): Unit = ???

  override def info(marker: Marker, format: String, arguments: AnyRef*): Unit = ???

  override def info(marker: Marker, msg: String, t: Throwable): Unit = ???
}


