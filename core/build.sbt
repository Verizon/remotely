
import common._

name := "remotely-core"

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
  "org.scodec"         %% "scodec-core"   % "1.8.1",
  "org.scodec"         %% "scodec-scalaz" % "1.1.0",
  "org.scalaz"         %% "scalaz-core"   % "7.1.0",
  "org.scalaz.stream"  %% "scalaz-stream" % "0.7a",
  "org.apache.commons" % "commons-pool2"  % "2.2",
  "io.netty"           % "netty-handler"  % "4.0.25.Final",
  "io.netty"           % "netty-codec"    % "4.0.25.Final"
)

macrosSettings

testSettings

promptSettings

bintrayPublishSettings

releaseSettings

publishMavenStyle := true

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

scmInfo := Some(ScmInfo(url("https://github.com/oncue/remotely"),
                        "git@github.com:oncue/remotely.git"))

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("remote", "functional programming", "rpc", "reasonable")

bintray.Keys.bintrayOrganization in bintray.Keys.bintray := Some("oncue")

bintray.Keys.repository in bintray.Keys.bintray := "releases"
