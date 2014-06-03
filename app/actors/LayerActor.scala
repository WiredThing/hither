package actors

import akka.actor.Actor
import models.ImageId
import play.api.libs.ws.WS
import java.io.{File, FileOutputStream}
import play.api.libs.iteratee.Iteratee
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Logger
import system.Configuration

case class CacheImage(imageId: ImageId)

class LayerActor extends Actor {
  override def receive = {
    case CacheImage(imageId) => {

      {
        val imageFile = Configuration.buildCachePath(imageId.id).get
        val imageUrl = s"http://registry-1.docker.io/v1/images/${imageId.id}/layer"

        if (imageFile.exists()) {
          Logger.info(s"Image is already cached in ${imageFile.getAbsolutePath}")
        } else {
          Logger.info(s"Caching image  into ${imageFile.getAbsolutePath()}")
          retrieveAndCache(imageUrl, imageFile)
        }
      }

      {
        val jsonFile = Configuration.buildCachePath(s"${imageId.id}.json").get
        val jsonUrl = s"http://registry-1.docker.io/v1/images/${imageId.id}/json"
        if (jsonFile.exists()) {
          Logger.info(s"Json is already cached in ${jsonFile.getAbsolutePath}")
        } else {
          Logger.info(s"Caching json into ${jsonFile.getAbsolutePath()}")
          retrieveAndCache(jsonUrl, jsonFile)
        }
      }
    }
  }


  def retrieveAndCache(url: String, file: File) = {
    WS.url(url).getStream().map {
      case (response, body) =>
        if (response.status == 200) {
          body run Iteratee.fold[Array[Byte], FileOutputStream](new FileOutputStream(file)) { (os, data) =>
            os.write(data)
            os
          }.map { os =>
            os.close()
            Right(file)
          }
        }
    }
  }
}
