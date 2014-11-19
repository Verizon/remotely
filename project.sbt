organization in Global := "oncue.svc.remotely"

scalaVersion in Global := "2.10.4"

resolvers += Resolver.sonatypeRepo("releases")

lazy val remotely = project.in(file(".")).aggregate(core, examples)

lazy val core = project

lazy val paradiseVersion = "2.0.1"

lazy val examples = project.dependsOn(core)

