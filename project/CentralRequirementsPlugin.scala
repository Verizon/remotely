//: ----------------------------------------------------------------------------
//: Copyright (C) 2016 Verizon.  All Rights Reserved.
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
package verizon.build

import sbt._, Keys._
import xerial.sbt.Sonatype.autoImport.sonatypeProfileName

object CentralRequirementsPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = RigPlugin

  override lazy val projectSettings = Seq(
    sonatypeProfileName := "io.verizon",
    pomExtra in Global := {
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
      </developers>
    },
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url("http://verizon.github.io/remotely/")),
    scmInfo := Some(ScmInfo(url("https://github.com/verizon/remotely"),
                                "git@github.com:verizon/remotely.git"))
  )
}
