organization in Global := "oncue.svc.remotely"

scalaVersion in Global := "2.10.4"

crossScalaVersions in Global := Seq("2.10.4", "2.11.5")

resolvers += Resolver.sonatypeRepo("releases")

lazy val remotely = project.in(file(".")).aggregate(core, examples, `benchmark-server`, `benchmark-client`, test)

lazy val core = project

lazy val examples = project dependsOn core

lazy val test = project dependsOn core

lazy val docs = project dependsOn core

lazy val `test-server` = project dependsOn test

lazy val `benchmark-protocol` = project.in(file("benchmark/protocol")).dependsOn(core)

lazy val `benchmark-server` = project.in(file("benchmark/server")).dependsOn(`benchmark-protocol`)

lazy val `benchmark-client` = project.in(file("benchmark/client")).dependsOn(`benchmark-protocol`, `benchmark-server`)

parallelExecution in Global := false

seq(bintraySettings:_*)

seq(bintrayPublishSettings:_*)

publishMavenStyle := true

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

scmInfo := Some(ScmInfo(url("https://github.com/oncue/remotely"),
  "git@github.com:oncue/remotely.git"))

bintray.Keys.packageLabels in bintray.Keys.bintray :=
Seq("remote", "functional programming", "rpc", "reasonable")

bintray.Keys.bintrayOrganization in bintray.Keys.bintray := Some("oncue")

bintray.Keys.repository in bintray.Keys.bintray := "releases"

osgiSettings

OsgiKeys.bundleSymbolicName := "remotely"

OsgiKeys.exportPackage := Seq("remotely.*")

OsgiKeys.importPackage := Seq(
  """scalaz.*;version="$<range;[==,=+);$<@>>"""",
  """io.netty.*;version="$<range;[==,=+);$<@>>"""",
  """scodec.*;version="$<range;[==,=+);$<@>>"""",
  "*"
)
