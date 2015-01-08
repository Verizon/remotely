
name := "core"

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

scalacOptions ++= Seq(
  "-Ywarn-value-discard",
  "-Xlint",
  "-deprecation",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps"
)

libraryDependencies ++= Seq(
  "org.typelevel"      %% "scodec-core"   % "1.5.0",
  "org.scalaz"         %% "scalaz-core"   % "7.1.0",
  "org.scalaz.stream"  %% "scalaz-stream" % "0.6a",
  "org.apache.commons" % "commons-pool2"  % "2.2",
  "io.netty"           % "netty"          % "3.6.6.Final"
)

lazy val paradiseVersion = "2.0.1"

addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)

libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" %_)

libraryDependencies += ("org.scalamacros" %% "quasiquotes" % paradiseVersion)

libraryDependencies ++= Seq(
  "org.scalatest"  %% "scalatest"  % "2.2.1"  % "test",
  "org.scalacheck" %% "scalacheck" % "1.11.6" % "test"
)
