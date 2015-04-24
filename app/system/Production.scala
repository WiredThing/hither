package system

import controllers.HitherS3Signer
import fly.play.s3.{S3Client, S3Configuration}
import play.api.libs.ws.WS
import play.api.{Application, Logger}
import system.index.S3Index
import system.registry.{Registry, S3Registry}

object Production {

  private val signer: HitherS3Signer = new HitherS3Signer(s3Config.credentials, s3Config.region, Configuration.aws.proxy)

  private lazy val s3Config = S3Configuration.fromConfig(play.api.Play.current)

  lazy val s3Index = new S3Index {
    override def bucketName: String = Configuration.s3.bucketName

    override implicit def app: Application = play.api.Play.current

    override lazy val s3Client = new S3Client(WS.client, signer, s3Config)

    override lazy val logger = Logger
  }


  lazy val index = Configuration.hither.storage match {
    case "s3" => s3Index
    case s => throw new IllegalArgumentException(s"Don't recognise storage type '$s'")
  }

  lazy val s3Registry = new S3Registry {

    override implicit def app: Application = play.api.Play.current

    override val bucketName: String = Configuration.s3.bucketName

    override def s3Client: S3Client = new S3Client(WS.client, signer, s3Config)

    override val logger = Logger

    Logger.debug("Initialising S3 registry")
    Logger.debug(s"Using aws.accessKeyId ${Configuration.aws.accessKeyId.map(s => obfuscate(s))}")
    Logger.debug(s"Using aws.secretKey ${Configuration.aws.secretKey.map(s => obfuscate(s))}")
    Logger.debug(s"Using region ${Configuration.s3.region}")
    Logger.debug(s"Using bucket $bucketName")
    Configuration.aws.proxy match {
      case Some(proxy) => Logger.debug(s"Using proxy server $proxy")
      case None => Logger.debug("Not using proxy server")
    }
  }

  def obfuscate(s: String, show: Int = 3): String = {
    val hide = if (s.length > show) s.length - show else s.length
    List.fill(hide)('*').mkString + s.substring(hide)
  }

  lazy val registry: Registry = Configuration.hither.storage match {
    case "s3" => s3Registry
    case s => throw new IllegalArgumentException(s"Don't recognise storage type '$s'")
  }

}
