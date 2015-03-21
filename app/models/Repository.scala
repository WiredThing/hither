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
  def apply(repoName: RepositoryName): Repository = Repository(Namespace.default, repoName)

  def qualifiedName(repo:Repository) : Option[String] = Some(repo.qualifiedName)
}

