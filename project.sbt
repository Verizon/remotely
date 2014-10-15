import oncue.build._

organization in Global := "oncue.svc.remotely"

scalaVersion in Global := "2.10.4"

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)

lazy val remotely = project.in(file(".")).aggregate(core, funnel, examples)

lazy val core = project.settings(
  addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full),
  libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
  libraryDependencies += ("org.scalamacros" %% "quasiquotes" % paradiseVersion)
)

lazy val paradiseVersion = "2.0.1"

lazy val funnel = project.dependsOn(core)

lazy val examples = project.dependsOn(core).settings(
  addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full),
  libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
  libraryDependencies += ("org.scalamacros" %% "quasiquotes" % paradiseVersion)
)

OnCue.baseSettings

Publishing.ignore
