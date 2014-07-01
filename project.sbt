import oncue.build._

organization in Global := "oncue.svc.remotely"

scalaVersion in Global := "2.10.4"

name := "core"

scalacOptions ++= Seq(
  "-language:existentials",
  "-language:postfixOps"
)

libraryDependencies ++= Seq(
  "org.scalaz.stream" %% "scalaz-stream" % "0.4.1",
  "org.typelevel"     %% "scodec-core"   % "1.1.0",
  "com.typesafe.akka" %% "akka-kernel"   % "2.2.+"
)

OnCue.baseSettings

ScalaCheck.settings
