//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
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
    ),

    unmanagedSourceDirectories in Compile +=
      (sourceDirectory in Compile).value / "macros" / s"scala-${scalaBinaryVersion.value}"
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
