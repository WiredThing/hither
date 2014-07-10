package system

import play.api.{Logger, Play}
import play.api.Play.current

object Configuration {

  lazy val storage = {
    val s = Play.configuration.getString("hither.storage").getOrElse("s3")
    Logger.debug(s"Configuring hither with $s storage")
    s
  }

  object file {
    lazy val indexRoot = Play.configuration.getString("file.registry.index").getOrElse("/tmp/index")

    lazy val registryRoot = Play.configuration.getString("file.registry.root").getOrElse("/tmp/registry")
  }

  object aws {
    lazy val accessKeyId = Play.configuration.getString("aws.accessKeyId").get
    lazy val secretKey = Play.configuration.getString("aws.secretKey").get
  }


  object s3 {
    lazy val indexRoot = Play.configuration.getString("s3.registry.index").getOrElse("index")

    lazy val registryRoot = Play.configuration.getString("s3.registry.root").getOrElse("registry")

    lazy val bucketName = Play.configuration.getString("s3.bucketName").get
    lazy val region = Play.configuration.getString("s3.region").get
  }

}
