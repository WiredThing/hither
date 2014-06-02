package services

import org.scalatest.{Matchers, FlatSpec}
import services._

class RepositoryTest extends FlatSpec with Matchers {

  "A Repository with a namespace" should "have a two-part urlString" in {
    val repo = Repository(Some(Namespace("foo")), RepositoryName("bar"))

    repo.urlString shouldBe "foo/bar"
  }

}
