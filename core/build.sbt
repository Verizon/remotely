import oncue.build._

name := "core"

scalacOptions ++= Seq(
  "-Ywarn-value-discard",
  "-Xlint",
  "-language:existentials",
  "-language:postfixOps"
)

libraryDependencies ++= Seq(
  "org.scalaz.stream" %% "scalaz-stream" % "0.5",
  "oncue.typelevel"     %% "scodec-core"   % "1.1.3",
  "com.typesafe.akka" %% "akka-actor"    % "2.2.4"
)

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

