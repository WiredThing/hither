package system

import play.api.Play.current
import play.api.libs.ws.{WSProxyServer, DefaultWSProxyServer}
import play.api.{Logger, Play}

object Configuration {

  object hither {
    lazy val allowAnonRepoCreation = Play.configuration.getBoolean("hither.allowAnonRepoCreation").getOrElse(false)
    lazy val storage = {
      val s = Play.configuration.getString("hither.storage").getOrElse("s3")
      Logger.debug(s"Configuring hither with $s storage")
      s
    }
  }

  object file {
    lazy val indexRoot = Play.configuration.getString("file.registry.index").getOrElse("/tmp/index")
    lazy val registryRoot = Play.configuration.getString("file.registry.root").getOrElse("/tmp/registry")
  }

  object aws {
    lazy val accessKeyId = Play.configuration.getString("aws.accessKeyId").get
    lazy val secretKey = Play.configuration.getString("aws.secretKey").get

    lazy val proxy: Option[WSProxyServer] = Play.configuration.getString("aws.proxyHost").map { hostName =>
      val port = Play.configuration.getInt("aws.proxyPort").getOrElse(80)
      DefaultWSProxyServer(hostName, port)
    }
  }

  object s3 {
    lazy val indexRoot = Play.configuration.getString("s3.registry.index").getOrElse("index")

    lazy val registryRoot = Play.configuration.getString("s3.registry.root").getOrElse("registry")

    lazy val bucketName = Play.configuration.getString("s3.bucketName").get
    lazy val region = Play.configuration.getString("s3.region").get
    lazy val useHttps: Boolean = Play.configuration.getBoolean("s3.https").getOrElse(false)
  }

}
