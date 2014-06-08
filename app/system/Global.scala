package system

import play.api.{Logger, Application, GlobalSettings}

object Global extends GlobalSettings{
  override def onStart(app: Application): Unit = {
    super.onStart(app)

    Logger.info("Creating registry and index directories")
    LocalRegistry.createDirs
    LocalIndex.createDirs
  }
}
