package system

import play.api.libs.concurrent.Akka
import actors.LayerActor
import akka.actor.Props
import play.api.Play.current

object AppSystem {

  lazy val layerActor = Akka.system.actorOf(Props[LayerActor], name = "layerActor")

}
