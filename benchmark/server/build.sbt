scalacOptions += "-language:reflectiveCalls"

addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.0" % "test"
