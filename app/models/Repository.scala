package models

case class RepositoryName(name: String) extends AnyVal

case class Namespace(name: String) extends AnyVal

object Namespace {
  val default = Namespace("library")
}

case class Repository(namespace: Namespace, repoName: RepositoryName) {
  val qualifiedName = s"${namespace.name}/${repoName.name}"
}

object Repository {
  def apply(namespace:Option[Namespace], repoName:RepositoryName) : Repository = {
    Repository(namespace.getOrElse(Namespace.default), repoName)
  }
}

