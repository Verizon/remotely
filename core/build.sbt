import oncue.build._

name := "core"

scalacOptions ++= Seq(
  "-language:existentials",
  "-language:postfixOps"
)

libraryDependencies ++= Seq(
  "org.scalaz.stream" %% "scalaz-stream" % "0.4.1",
  "org.typelevel"     %% "scodec-core"   % "1.1.0",
  "com.typesafe.akka" %% "akka-actor"    % "2.2.4"
)

OnCue.baseSettings

ScalaCheck.settings
