package system

import play.api.Play
import play.api.Play.current

object Configuration {

  object file {
    lazy val indexRoot = Play.configuration.getString("registry.index").getOrElse("/tmp/index")

    lazy val registryRoot = Play.configuration.getString("registry.root").getOrElse("/tmp/registry")
  }


  object s3 {

    lazy val indexRoot = Play.configuration.getString("s3.registry.index").getOrElse("index")

    lazy val registryRoot = Play.configuration.getString("s3.registry.root").getOrElse("registry")

    lazy val bucketName = Play.configuration.getString("s3.bucketName").get
    lazy val accessKeyId = Play.configuration.getString("aws.accessKeyId").get
    lazy val secretKey = Play.configuration.getString("aws.secretKey").get
    lazy val region = Play.configuration.getString("s3.region").get
  }

}
