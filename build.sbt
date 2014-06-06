import play.PlayImport.PlayKeys._

name := "hither"

version := "0.1.3-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

routesImport += "binders._,  models._"

scalaVersion := "2.11.1"


libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatest" %% "scalatest" % "2.1.7" % "test"
)


