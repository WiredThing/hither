package controllers

import org.scalatest.{Matchers, FlatSpec}

class S3Controller$Spec extends FlatSpec with Matchers {

  "identifyOrphanedJsons" should "identify one orphan" in {
    val testData = Seq("a.json", "a.layer", "b.json", "b.layer", "c.json")

    S3Controller.identifyOrphanedJsons(testData) shouldBe Seq("c.json")
  }


  it should "identify one orphan when .checksum files are present and files have paths" in {
    val testData = (Seq("a", "b").flatMap(i => Seq(i + ".layer", i + ".json", i + ".checksum")) ++ Seq("c.json", "c.checksum")).map("registryRoot/" + _)
    println(testData)

    S3Controller.identifyOrphanedJsons(testData) shouldBe Seq("registryRoot/c.json")
  }

}
