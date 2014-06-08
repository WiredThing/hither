package services

import models._
import scala.concurrent.Future

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import play.api.libs.json.JsValue

object TagService {
  def getTag(repo: Repository, tagName: String): Future[JsValue] = {
    WS.url(s"http://registry-1.docker.io/v1/repositories/${repo.qualifiedName}/tags/$tagName").get().map {
      response => response.json
    }
  }

  def getTags(repo: Repository): Future[JsValue] = {
    WS.url(s"http://registry-1.docker.io/v1/repositories/${repo.qualifiedName}/tags").get().map {
      response => response.json
    }
  }
}
