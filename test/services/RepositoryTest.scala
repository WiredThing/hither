package services

import org.scalatest.{Matchers, FlatSpec}
import models.{RepositoryName, Repository, Namespace}

class RepositoryTest extends FlatSpec with Matchers {

  "A Repository with a namespace" should "have a two-part urlString" in {
    val repo = Repository(Some(Namespace("foo")), RepositoryName("bar"))

    repo.qualifiedName shouldBe "foo/bar"
  }

}
