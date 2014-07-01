import oncue.build._

organization in Global := "oncue.svc.remotely"

scalaVersion in Global := "2.10.4"

lazy val remotely = project.in(file(".")).aggregate(core, funnel, examples)

lazy val core = project

lazy val funnel = project.dependsOn(core)

lazy val examples = project.dependsOn(core)

OnCue.baseSettings

Publishing.ignore
