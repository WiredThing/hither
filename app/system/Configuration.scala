package system

import java.io.File

import models.Repository
import play.api.Play
import play.api.Play.current

object Configuration {

  lazy val indexRoot = Play.configuration.getString("registry.index").getOrElse("/tmp/index")

  lazy val registryRoot = Play.configuration.getString("registry.root").getOrElse("/tmp/registry")


  object s3 {
    lazy val bucketName = Play.configuration.getString("s3.bucketName").get
    lazy val accessKeyId = Play.configuration.getString("aws.accessKeyId").get
    lazy val secretKey = Play.configuration.getString("aws.secretKey").get
    lazy val region = Play.configuration.getString("s3.region").get
  }




  def buildRepoIndexPath(repo: Repository): File = {
    val indexRoot = new File(system.Configuration.indexRoot)

    val path = new File(new File(indexRoot, repo.namespace.name), repo.repoName.name)

    path.mkdirs()

    path
  }

}
