import common._

scalacOptions ++= Seq(
  "-language:existentials",
  "-language:postfixOps"
)

name := "remotely-examples"

macrosSettings

promptSettings

publish := {}
