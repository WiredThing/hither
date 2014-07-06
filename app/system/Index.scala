package system

import models.Repository
import play.api.libs.iteratee.Iteratee
import system.registry.ResourceType

import scala.concurrent.ExecutionContext


object IndexType {
  val ImagesType = ResourceType("images", "application/json")
}


trait Index {
  def sinkFor(repository: Repository, resourceType: ResourceType, option: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = ???
}
