package actors

import akka.actor.Actor
import models.ImageId
import play.api.libs.ws.WS
import java.io.{File, FileOutputStream}
import play.api.libs.iteratee.Iteratee
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Logger
import system.{Registry, Configuration}
import system.Registry.{CacheFile, LocalSource}

case class CacheImage(imageId: ImageId)

class LayerActor extends Actor {
  override def receive = {
    case CacheImage(imageId) => {

      {
        val imageSource = Registry.buildCachePath(imageId.id)
        val imageUrl = s"http://registry-1.docker.io/v1/images/${imageId.id}/layer"

        if (imageSource.exists()) {
          Logger.info(s"Image is already cached in ${imageSource.getAbsolutePath}")
        } else {
          Logger.info(s"Caching image  into ${imageSource.getAbsolutePath()}")
          retrieveAndCache(imageUrl, imageSource)
        }
      }

      {
        val jsonSource = Registry.buildCachePath(s"${imageId.id}.json")
        val jsonUrl = s"http://registry-1.docker.io/v1/images/${imageId.id}/json"
        if (jsonSource.exists()) {
          Logger.info(s"Json is already cached in ${jsonSource.getAbsolutePath}")
        } else {
          Logger.info(s"Caching json into ${jsonSource.getAbsolutePath()}")
          retrieveAndCache(jsonUrl, jsonSource)
        }
      }
    }
  }


  def retrieveAndCache(url: String, source: CacheFile) = {
    WS.url(url).getStream().map {
      case (response, body) =>
        if (response.status == 200) {
          body run Iteratee.fold[Array[Byte], FileOutputStream](new FileOutputStream(source.file)) { (os, data) =>
            os.write(data)
            os
          }.map { os =>
            os.close()
            Right(source.file)
          }
        }
    }
  }
}
