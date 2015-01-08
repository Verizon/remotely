val paradiseVersion = "2.0.1" 

addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)

libraryDependencies ++= Seq(
  "org.scalatest"  % "scalatest_2.10" % "2.2.1"  % "test",
  "org.scalacheck" %% "scalacheck"    % "1.11.6" % "test"
)
