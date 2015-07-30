
import common._

resolvers += Resolver.sonatypeRepo("public")

resolvers += Resolver.bintrayRepo("scalaz", "releases")

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
  "org.scodec"         %% "scodec-core"   % "1.8.1",
  "org.scodec"         %% "scodec-scalaz" % "1.1.0",
  "org.scalaz"         %% "scalaz-core"   % "7.1.0",
  "org.scalaz.stream"  %% "scalaz-stream" % "0.7a",
  "org.apache.commons" % "commons-pool2"  % "2.2",
  "io.netty"           % "netty-handler"  % "4.0.25.Final",
  "io.netty"           % "netty-codec"    % "4.0.25.Final"
)

common.macrosSettings

common.settings