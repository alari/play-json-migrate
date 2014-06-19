organization := "play-infra"

name := "play-json-migrate"

version := "0.3.0"

crossScalaVersions := Seq("2.10.4", "2.11.1")


libraryDependencies ++= {
  Seq(
    "com.typesafe.play" %% "play-json" % "2.3.0",
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