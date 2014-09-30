
import oncue.build._

scalacOptions ++= Seq(
  "-language:existentials",
  "-language:postfixOps"
)

name := "examples"

Publishing.ignore
