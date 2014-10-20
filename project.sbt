import oncue.build._

organization in Global := "oncue.svc.remotely"

scalaVersion in Global := "2.10.4"

resolvers += Resolver.sonatypeRepo("releases")

lazy val remotely = project.in(file(".")).aggregate(core, funnel, examples)

lazy val core = project

lazy val paradiseVersion = "2.0.1"

lazy val funnel = project.dependsOn(core)

lazy val examples = project.dependsOn(core)

OnCue.baseSettings

Publishing.ignore
