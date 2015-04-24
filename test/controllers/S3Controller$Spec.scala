package controllers

import org.scalatest._

class S3Controller$Spec extends FlatSpec with Matchers {

  "trimQuotes" should "remove 1 double-quote from the start of a string" in {
    S3Controller.trimQuotes("\"foo") shouldBe "foo"
  }

  it should "remove 2 double-quotes from the start of a string" in {
    S3Controller.trimQuotes("\"\"foo") shouldBe "foo"
  }

  it should "remove 1 single-quote from the start of a string" in {
    S3Controller.trimQuotes("'foo") shouldBe "foo"
  }

  it should "remove 2 single-quotes from the start of a string" in {
    S3Controller.trimQuotes("''foo") shouldBe "foo"
  }

  it should "remove mixed quotes from the start of a string" in {
    S3Controller.trimQuotes("'\"'foo") shouldBe "foo"
  }

  it should "remove 1 double-quote the end of a string" in {
    S3Controller.trimQuotes("foo\"") shouldBe "foo"
  }
  it should "remove 2 double-quotes the end of a string" in {
    S3Controller.trimQuotes("foo\"\"") shouldBe "foo"
  }

  it should "remove 1 single-quote from the end of a string" in {
    S3Controller.trimQuotes("foo'") shouldBe "foo"
  }

  it should "remove 2 single-quotes from the end of a string" in {
    S3Controller.trimQuotes("foo''") shouldBe "foo"
  }

  it should "remove mixed quotes from the end of a string" in {
    S3Controller.trimQuotes("foo'\"'") shouldBe "foo"
  }

  it should "remove quotes from start and end of string" in {
    S3Controller.trimQuotes("\"''\"foo'\"'") shouldBe "foo"
  }

  it should "leave quotes in the middle of the string unchanged" in {
    S3Controller.trimQuotes("foo\"'bar") shouldBe "foo\"'bar"
  }

}
