import sbt._
import Keys._

object common {

  def macrosSettings = Seq(
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full),
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    ) ++ (
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, scalaMajor)) if scalaMajor == 10 => Seq("org.scalamacros" %% "quasiquotes" % "2.1.0-M5")
        case _ => Nil
      }
    )
  )

  val scalaTestVersion  = SettingKey[String]("scalatest version")
  val scalaCheckVersion = SettingKey[String]("scalacheck version")

  def testSettings = Seq(
    scalaTestVersion     := "2.2.1",
    scalaCheckVersion    := "1.11.6",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"  % scalaTestVersion.value  % "test",
      "org.scalacheck" %% "scalacheck" % scalaCheckVersion.value % "test"
    )
  )

}