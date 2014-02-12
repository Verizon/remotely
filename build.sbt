organization := "srpc"

name := "srpc"

version := "snapshot-0.1"

scalaVersion := "2.10.3"

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps"
)

resolvers ++= Seq(Resolver.sonatypeRepo("releases"), Resolver.sonatypeRepo("snapshots"))

libraryDependencies ++= Seq(
  "org.scalaz.stream" %% "scalaz-stream" % "snapshot-0.4",
  "com.github.scodec" %% "scodec" % "1.0.0-M1"
)

seq(bintraySettings:_*)

publishMavenStyle := true

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

bintray.Keys.packageLabels in bintray.Keys.bintray :=
  Seq("stream processing", "functional I/O", "iteratees", "functional programming", "scala")
