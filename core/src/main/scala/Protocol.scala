//: ----------------------------------------------------------------------------
//: Copyright (C) 2014 Verizon.  All Rights Reserved.
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

package remotely

import scala.reflect.runtime.universe.TypeTag
import scodec.{Codec,Decoder,Encoder}

case class Protocol(codecs: Codecs, signatures: Signatures) {

  def codec[A:TypeTag:Codec]: Protocol =
    this.copy(codecs = codecs.codec[A])

  def specify[A:TypeTag](name: String): Protocol =
    this.copy(signatures = signatures.specify[A](name))

  def specify1[A:TypeTag,B:TypeTag](name: String): Protocol =
    specify[A => B](name)

  def specify2[A:TypeTag,B:TypeTag,C:TypeTag](name: String): Protocol =
    specify[(A,B) => C](name)

  def specify3[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag](name: String): Protocol =
    specify[(A,B,C) => D](name)

  def specify4[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag](name: String): Protocol =
    specify[(A,B,C,D) => E](name)

  def generateClient(moduleName: String, pkg: String = "default"): String =
    signatures.generateClient(moduleName, pkg)

  def generateServer(traitName: String, pkg: String = "default"): String = s"""
  |package $pkg
  |
  |import remotely.{Codecs,Environment,Response,Values}
  |import remotely.codecs._
  |
  |abstract class $traitName {
  |  // This interface is generated from a `Protocol`. Do not modify.
  |  def environment: Environment = Environment(
  |${Signatures.indent("    ")(codecs.pretty)},
  |    populateDeclarations(Values.empty)
  |  )
  |
  |${Signatures.indent("  ")(signatures.generateServerTraitBody)}
  |}
  """.stripMargin

  def pretty: String =
    "Protocol(\n" +
    Signatures.indent("  ")(codecs.pretty) + ",\n" +
    Signatures.indent("  ")(signatures.pretty) + "\n)"

  override def toString = pretty
}

object Protocol {

  val empty = Protocol(Codecs.empty, Signatures.empty)
}
