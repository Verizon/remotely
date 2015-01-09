
scalacOptions ++= Seq(
  "-language:existentials",
  "-language:postfixOps"
)

name := "examples"

addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)

libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" %_)

libraryDependencies += ("org.scalamacros" %% "quasiquotes" % "2.0.1")
