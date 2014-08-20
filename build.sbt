import play.PlayImport.PlayKeys._

organization := "com.wiredthing"

name := "hither"

version := Option(System.getenv("HITHER_VERSION")).getOrElse("999-SNAPSHOT")

lazy val root = (project in file(".")).enablePlugins(PlayScala)

routesImport += "binders._,  models._"

scalaVersion := "2.11.1"

scalacOptions ++= Seq("-feature")

resolvers ++= Seq(
  "WiredThing Internal Forks Repository" at "http://artifactory.wiredthing.com/artifactory/libs-forked-local"
)

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

libraryDependencies ++= Seq(
  ws,
  "org.scala-lang.modules" %% "scala-xml" % "1.0.1",
  "nl.rhinofly" %% "play-s3" % "5.0.0-FORKED",
  "org.scalatest" %% "scalatest" % "2.1.7" % "test"
)
