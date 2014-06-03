package actors

import akka.actor.Actor
import models.ImageId
import play.api.libs.ws.WS
import java.io.FileOutputStream
import play.api.libs.iteratee.Iteratee
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Logger
import system.Configuration

case class CacheImage(imageId: ImageId)

class LayerActor extends Actor {
  override def receive = {
    case CacheImage(imageId) => {
      val url = s"http://registry-1.docker.io/v1/images/${imageId.id}/layer"

      val layerFile = Configuration.buildCachePath(imageId.id).get

      if (layerFile.exists()) {
        Logger.info(s"Image ${imageId.id} is already cached in ${layerFile.getAbsolutePath}")
      } else {

        Logger.info(s"Caching image ${imageId.id} into ${layerFile.getAbsolutePath()}")

        // Make the request
        WS.url(url).getStream().map {
          case (response, body) =>
            // Check that the response was successful
            if (response.status == 200) {

              // Get the content type
              val contentType = response.headers.get("Content-Type").flatMap(_.headOption)
                .getOrElse("application/octet-stream")

              //Ok.feed(body).as(contentType).withHeaders("Content-Length" -> length)
              body run Iteratee.fold[Array[Byte], FileOutputStream](new FileOutputStream(layerFile)) { (os, data) =>
                os.write(data)
                os
              }.map { os =>
                os.close()
                Right(layerFile)
              }
            }
        }
      }
    }
  }


}
