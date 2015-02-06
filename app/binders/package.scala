import models._
import play.api.mvc.PathBindable

package object binders {
  implicit def repoBinder: PathBindable[Repository] = new PathBindable[Repository] {
    override def bind(key: String, value: String): Either[String, Repository] = {
      value.split("/").toList match {
        case repoName +: Nil => Right(Repository(RepositoryName(repoName)))
        case namespace +: repoName +: Nil => Right(Repository(Namespace(namespace), RepositoryName(repoName)))
        case _ => Left(s"Could not convert $value to a Repository")
      }
    }

    override def unbind(key: String, repo: Repository): String = {
      repo match {
        case Repository(Namespace.default, repoName) => repoName.name
        case Repository(namespace, repoName) => s"${namespace.name}/${repoName.name}"
      }
    }
  }

  implicit def imageIdBinder: PathBindable[ImageId] = new PathBindable[ImageId] {
    override def bind(key: String, value: String): Either[String, ImageId] =
      implicitly[PathBindable[String]].bind(key, value).fold(
        left => Left(left),
        right => Right(ImageId(right))
      )

    def unbind(key: String, value: ImageId): String = value.id
  }

}
