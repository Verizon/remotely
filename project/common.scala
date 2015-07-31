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
import sbt._, Keys._
import sbtrelease._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.Utilities._
import com.typesafe.sbt.pgp.PgpKeys._
import bintray.BintrayKeys._
import sbtassembly.Plugin._

object common {
  import AssemblyKeys._

  def settings =
    bintraySettings ++
    releaseSettings ++
    publishingSettings ++
    testSettings

  def mergeSettings = Seq(
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
      {
        case "META-INF/io.netty.versions.properties" => MergeStrategy.discard
        case x => old(x)
      }
    }
  )

  def macrosSettings = Seq(
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full),
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
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
    scalaTestVersion     := "2.2.5",
    scalaCheckVersion    := "1.11.6",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"  % scalaTestVersion.value  % "test",
      "org.scalacheck" %% "scalacheck" % scalaCheckVersion.value % "test"
    )
  )

  def ignoreSettings = Seq(
    publish := (),
    publishSigned := (),
    publishLocal := (),
    publishLocalSigned := (),
    publishArtifact in Test := false,
    publishArtifact in Compile := false
  )

  def bintraySettings = Seq(
    bintrayPackageLabels := Seq("remote", "functional programming", "rpc", "reasonable"),
    bintrayOrganization := Some("oncue"),
    bintrayRepository := "releases",
    bintrayPackage := "remotely"
  )

  def releaseSettings = Seq(
    releaseCrossBuild := true,
    releasePublishArtifactsAction := publishSigned.value,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )

  def publishingSettings = Seq(
    pomExtra := (
      <developers>
        <developer>
          <id>timperrett</id>
          <name>Timothy Perrett</name>
          <url>http://github.com/timperrett</url>
        </developer>
        <developer>
          <id>runarorama</id>
          <name>Runar Bjarnason</name>
          <url>http://github.com/runarorama</url>
        </developer>
        <developer>
          <id>stew</id>
          <name>Stew O'Connor</name>
          <url>http://github.com/stew</url>
        </developer>
        <developer>
          <id>ahjohannessen</id>
          <name>Alex Henning Johannessen</name>
          <url>https://github.com/ahjohannessen</url>
        </developer>
        <developer>
          <id>pchiusano</id>
          <name>Paul Chiusano</name>
          <url>https://github.com/pchiusano</url>
        </developer>
        <developer>
          <id>jedesah</id>
          <name>Jean-Rémi Desjardins</name>
          <url>https://github.com/jedesah</url>
        </developer>
      </developers>),
    publishMavenStyle := true,
    useGpg := true,
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url("http://oncue.github.io/remotely/")),
    scmInfo := Some(ScmInfo(url("https://github.com/oncue/remotely"),
                                "git@github.com:oncue/remotely.git")),
    pomIncludeRepository := { _ => false },
    publishArtifact in Test := false
  )

  def promptSettings = Prompt.settings
}
