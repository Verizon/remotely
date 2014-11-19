
scalacOptions ++= Seq(
  "-language:existentials",
  "-language:postfixOps"
)

name := "examples"

lazy val paradiseVersion = "2.0.1"

addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)

libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" %_)

libraryDependencies += ("org.scalamacros" %% "quasiquotes" % paradiseVersion)
