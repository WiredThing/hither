package models

case class RepositoryName(name: String) extends AnyVal

case class Namespace(name: String) extends AnyVal

case class Repository(namespace: Option[Namespace], repoName: RepositoryName) {
  val urlString = namespace.map { ns =>
    s"${ns.name}/${repoName.name}"
  }.getOrElse {
    repoName.name
  }
}
