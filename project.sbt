
organization in Global := "oncue.remotely"

scalaVersion in Global := "2.10.5"

crossScalaVersions in Global := Seq("2.10.5", "2.11.6")

resolvers += Resolver.sonatypeRepo("releases")

lazy val remotely = project.in(file(".")).aggregate(core, examples, `benchmark-server`, `benchmark-client`, test, `test-server`).settings(publish := {})

lazy val core = project

lazy val examples = project dependsOn core

lazy val test = project dependsOn core

lazy val docs = project dependsOn core

lazy val `test-server` = project dependsOn test

lazy val `benchmark-protocol` = project.in(file("benchmark/protocol")).dependsOn(core)

lazy val `benchmark-server` = project.in(file("benchmark/server")).dependsOn(`benchmark-protocol`)

lazy val `benchmark-client` = project.in(file("benchmark/client")).dependsOn(`benchmark-protocol`, `benchmark-server`)

parallelExecution in Global := false

common.promptSettings

releaseCrossBuild := true

publishArtifact in (Compile, packageBin) := false

publish := ()

publishLocal := ()
