import play.PlayImport.PlayKeys._

organization := "com.wiredthing"

name := "hither"

version := Option(System.getenv("HITHER_VERSION")).getOrElse("999-SNAPSHOT")

lazy val root = (project in file(".")).enablePlugins(PlayScala)

routesImport += "binders._,  models._"

scalaVersion := "2.11.1"

scalacOptions ++= Seq("-feature")

resolvers += "Local Artifactory" at "http://192.168.59.103:8081"

resolvers += "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"

publishTo := Some("Artifactory Realm" at "http://192.168.59.103:8081/artifactory/simple/wiredthing-snapshot/")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scala-lang.modules" %% "scala-xml" % "1.0.1",
  "nl.rhinofly" %% "play-s3" % "5.0.0",
  "org.scalatest" %% "scalatest" % "2.1.7" % "test"
)


