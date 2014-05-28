organization := "play-infra"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.4"

sbtVersion := "0.13.1"

name := "play-json-migrate"

version := "1.0"

libraryDependencies ++= {
  Seq(
    "com.typesafe.play" %% "play-json" % "2.2.3",
    "org.specs2" %% "specs2" % "2.3.12" % "test"
  )
}

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

scalacOptions in Test ++= Seq("-Yrangepos")

publishTo := Some(Resolver.file("file",  new File( "/mvn-repo" )) )

testOptions in Test += Tests.Argument("junitxml")