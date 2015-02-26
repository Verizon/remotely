import common._

name := "core"

resolvers += "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"

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
  "org.scodec"         %% "scodec-core"   % "1.7.0",
  "org.scodec"         %% "scodec-scalaz" % "1.0.0",
  "org.scalaz"         %% "scalaz-core"   % "7.1.0",
  "org.scalaz.stream"  %% "scalaz-stream" % "0.6a",
  "org.apache.commons" % "commons-pool2"  % "2.2",
  "io.netty"           % "netty-handler"  % "4.0.25.Final",
  "io.netty"           % "netty-codec"    % "4.0.25.Final"
)

macrosSettings

testSettings
