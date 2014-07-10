package system

import play.api.{Application, GlobalSettings, Logger}

object Global extends GlobalSettings{
  override def onStart(app: Application): Unit = {
    super.onStart(app)

    Logger.info("Creating registry and index directories")
    Production.registry.init()
    Production.index.init()
  }
}
