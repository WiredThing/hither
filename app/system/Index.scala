package system

import models.Repository
import play.api.libs.iteratee.Iteratee
import system.registry.ResourceType

import scala.concurrent.{Future, ExecutionContext}


object IndexTypes {
  val ImagesType = ResourceType("images", "application/json")
}


trait Index {

  def writeTag(repo:Repository, tagName:String, value:String)(implicit ctx: ExecutionContext) : Future[Unit]

  def sinkFor(repository: Repository, resourceType: ResourceType, option: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = ???
}
