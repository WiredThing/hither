package binders

import org.scalatest.{Matchers, FlatSpec}
import models.{Namespace, RepositoryName, Repository}


class BindersSpec extends FlatSpec with Matchers {

  "repoBinder" should "bind 'foo' to a Repository with no namespace" in {
    repoBinder.bind("", "foo") shouldBe Right(Repository(RepositoryName("foo")))
  }

  it should "bind 'foo/bar' to a Repository with namespace 'foo' and name 'bar'" in {
    repoBinder.bind("", "foo/bar") shouldBe Right(Repository(Namespace("foo"), RepositoryName("bar")))
  }

  it should "fail to bind 'foo/bar/baz'" in {
    repoBinder.bind("", "foo/bar/baz") shouldBe a[Left[String, _]]
  }
}
