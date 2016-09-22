
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
  "org.scodec"         %% "scodec-core"   % "1.10.2",
  "org.scodec"         %% "scodec-scalaz" % "1.3.0a",
  "org.scalaz"         %% "scalaz-core"   % "7.2.6",
  "org.scalaz.stream"  %% "scalaz-stream" % "0.8.4a",
  "org.apache.commons" % "commons-pool2"  % "2.4.2",
  "io.netty"           % "netty-handler"  % "4.1.5.Final",
  "io.netty"           % "netty-codec"    % "4.1.5.Final"
)

common.macrosSettings

common.settings
