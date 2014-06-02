import models.{RepositoryName, Namespace}
import play.api.mvc.PathBindable

package object binders {

  implicit def OptionBindable[T : PathBindable] = new PathBindable[Option[T]] {
    def bind(key: String, value: String): Either[String, Option[T]] =
      implicitly[PathBindable[T]].
        bind(key, value).
        fold(
          left => Left(left),
          right => Right(Some(right))
        )

    def unbind(key: String, value: Option[T]): String = value map (_.toString) getOrElse ""
  }

  implicit def namespaceBinder = new PathBindable[Namespace] {
    override def bind(key: String, value: String): Either[String, Namespace] =
      implicitly[PathBindable[String]].
        bind(key, value).
        fold(
          left => Left(left),
          right => Right(Namespace(right))
        )

    def unbind(key: String, value: Namespace): String = value.name
  }

  implicit def repositoryNameBinder = new PathBindable[RepositoryName] {
    override def bind(key: String, value: String): Either[String, RepositoryName] =
      implicitly[PathBindable[String]].
        bind(key, value).
        fold(
          left => Left(left),
          right => Right(RepositoryName(right))
        )

    def unbind(key: String, value: RepositoryName): String = value.name
  }

}
