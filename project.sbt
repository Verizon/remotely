import oncue.build._

organization in Global := "oncue.svc.remotely"

scalaVersion in Global := "2.10.4"

lazy val remotely = project.in(file(".")).aggregate(core, examples)

lazy val core = project

lazy val examples = project.dependsOn(core)

OnCue.baseSettings

Publishing.ignore
