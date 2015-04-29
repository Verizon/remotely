import common._

promptSettings

assemblySettings

mergeSettings

scalacOptions ++= Seq("-language:postfixOps", "-language:reflectiveCalls")

publish := {}

libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value