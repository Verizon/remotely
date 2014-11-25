import oncue.build._

OnCue.baseSettings

ScalaTest.settings

scalacOptions += "-language:reflectiveCalls"

val paradiseVersion = "2.0.1" 

addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)
