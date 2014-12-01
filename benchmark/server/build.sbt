scalacOptions += "-language:reflectiveCalls"

val paradiseVersion = "2.0.1" 

addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.0" % "test"
