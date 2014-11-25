name := "core"

lazy val paradiseVersion = "2.0.1"

scalacOptions ++= Seq(
  "-Ywarn-value-discard",
  "-Xlint",
  "-language:existentials",
  "-language:postfixOps"
)

libraryDependencies ++= Seq(
  "org.scalaz.stream"  %% "scalaz-stream" % "0.5",
  "org.typelevel"      %% "scodec-core"   % "1.5.0",
  "org.apache.commons" % "commons-pool2"  % "2.2",
  "io.netty"           % "netty"          % "3.6.6.Final"
)

addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)

libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" %_)

libraryDependencies += ("org.scalamacros" %% "quasiquotes" % paradiseVersion)

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.11.6" % "test"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.2.1" % "test"
