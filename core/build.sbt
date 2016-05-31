
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
  "org.scodec"         %% "scodec-core"   % "1.10.0",
  "org.scodec"         %% "scodec-scalaz" % "1.3.0",
  "org.scalaz"         %% "scalaz-core"   % "7.1.8",
  "org.scalaz.stream"  %% "scalaz-stream" % "0.8.1",
  "org.apache.commons" % "commons-pool2"  % "2.4.2",
  "io.netty"           % "netty-handler"  % "4.1.0.Final",
  "io.netty"           % "netty-codec"    % "4.1.0.Final"
)

common.macrosSettings

common.settings
