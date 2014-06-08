package services

import models.{Namespace, RepositoryName}
import scala.concurrent.Future

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import play.api.libs.json.JsValue

object TagService {
  def getTag(repository: RepositoryName, tagName: String): Future[JsValue] = {
    WS.url(s"http://registry-1.docker.io/v1/repositories/$repository/tags/$tagName").get().map {
      response => response.json
    }
  }


  def getTag(namespace: Namespace, repository: RepositoryName, tagName: String): Future[JsValue] = {
    WS.url(s"http://registry-1.docker.io/v1/repositories/$namespace/$repository/tags/$tagName").get().map {
      response => response.json
    }
  }

  def getTags(namespace: Namespace, repository: RepositoryName): Future[JsValue] = {
    WS.url(s"http://registry-1.docker.io/v1/repositories/${namespace.name}/${repository.name}/tags").get().map {
      response => response.json
    }
  }

  def getTags(repository: RepositoryName): Future[JsValue] = {
    WS.url(s"http://registry-1.docker.io/v1/repositories/$repository/tags").get().map {
      response => response.json
    }
  }
}
