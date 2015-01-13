
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
  "org.typelevel"      %% "scodec-core"    % "1.5.0",
  "org.scalaz"         %% "scalaz-core"    % "7.1.0",
  "org.scalaz.stream"  %% "scalaz-stream"  % "0.6a",
  "org.apache.commons" % "commons-pool2"   % "2.2",
  "io.netty"           % "netty-transport" % "4.0.25.Final",
  "io.netty"           % "netty-codec"     % "4.0.25.Final"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)

libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" %_)

libraryDependencies += ("org.scalamacros" %% "quasiquotes" % "2.0.1")

libraryDependencies ++= Seq(
  "org.scalatest"  %% "scalatest"  % "2.2.1"  % "test",
  "org.scalacheck" %% "scalacheck" % "1.11.6" % "test"
)
