import play.PlayImport.PlayKeys._

organization := "com.wiredthing"

name := "hither"

version := IO.read(file("version")).trim()

enablePlugins(PlayScala)

routesImport += "binders._,  models._"

scalaVersion := "2.11.5"

scalacOptions ++= Seq("-feature")

resolvers ++= Seq(
  "WiredThing Internal Forks Repository" at "https://wiredthing.artifactoryonline.com/wiredthing/libs-forked-local",
  "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"
)

//credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

libraryDependencies ++= Seq(
  ws withSources(),
  "org.scala-lang.modules" %% "scala-xml" % "1.0.1",
  "nl.rhinofly" %% "play-s3" % "6.0.0-RC1" withSources(),
  "org.scalatest" %% "scalatest" % "2.1.7" % "test"
)
