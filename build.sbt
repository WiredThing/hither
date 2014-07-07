import play.PlayImport.PlayKeys._

name := "hither"

version := Option(System.getenv("HITHER_VERSION")).getOrElse("999-SNAPSHOT")

lazy val root = (project in file(".")).enablePlugins(PlayScala)

routesImport += "binders._,  models._"

scalaVersion := "2.11.1"

scalacOptions ++= Seq("-feature")

resolvers += "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "nl.rhinofly" %% "play-s3" % "5.0.0",
  "org.scalatest" %% "scalatest" % "2.1.7" % "test"
)


