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
import scodec.Codec

case class Protocol(codecs: Codecs, signatures: Signatures) {

  def codec[A:TypeTag:Codec]: Protocol =
    this.copy(codecs = codecs.codec[A])

  def specify0[O](name: String, out: Type[O]): Protocol =
    this.copy(signatures = signatures.specify0(name, out.name))

  def specify1[A,O](name: String, in: Field[A], out: Type[O]): Protocol =
    this.copy(signatures = signatures.specify1(name, in, out.name))

  def specify2[A,B,O](name: String, in1: Field[A], in2: Field[B], out: Type[O]): Protocol =
    this.copy(signatures = signatures.specify2(name, in1, in2, out.name))

  def specify3[A,B,C,O](name: String, in1: Field[A], in2: Field[B], in3: Field[C], out: Type[O]): Protocol =
    this.copy(signatures = signatures.specify3(name, in1, in2, in3, out.name))

  def specify4[A,B,C,D,O](name: String, in1: Field[A], in2: Field[B], in3: Field[C], in4: Field[D], out: Type[O]): Protocol =
    this.copy(signatures = signatures.specify4(name, in1, in2, in3, in4, out.name))

  def specify5[A,B,C,D,E,O](name: String, in1: Field[A], in2: Field[B], in3: Field[C], in4: Field[D], in5: Field[E], out: Type[O]): Protocol =
    this.copy(signatures = signatures.specify5(name, in1, in2, in3, in4, in5, out.name))

  def pretty: String =
    "Protocol(\n" +
    Signatures.indent("  ")(codecs.pretty) + ",\n" +
    Signatures.indent("  ")(signatures.pretty) + "\n)"

  override def toString = pretty
}

object Protocol {

  val empty = Protocol(Codecs.empty, Signatures.empty)

  /// Convenience Syntax

  implicit class ProtocolOps(inner: Protocol) {

    import Field._

    def ++(other: Protocol): Protocol = Protocol(
      codecs     = inner.codecs ++ other.codecs,
      signatures = Signatures(inner.signatures.signatures ++ other.signatures.signatures)
    )

    def define0[O: TypeTag](name: String): Protocol =
      inner.specify0(name, Type[O])

    def define1[A: TypeTag, O: TypeTag](name: String): Protocol = {

      val f1 = strict[A]("in1")

      inner.specify1(name, f1, Type[O])
    }

    def define2[A: TypeTag, B: TypeTag, O: TypeTag](name: String): Protocol = {

      val f1 = strict[A]("in1")
      val f2 = strict[B]("in2")

      inner.specify2(name, f1, f2, Type[O])
    }

    def define3[A: TypeTag, B: TypeTag, C: TypeTag, O: TypeTag](name: String): Protocol = {

      val f1 = strict[A]("in1")
      val f2 = strict[B]("in2")
      val f3 = strict[C]("in3")

      inner.specify3(name, f1, f2, f3, Type[O])
    }

    def define4[A: TypeTag, B: TypeTag, C: TypeTag, D: TypeTag, O: TypeTag](name: String): Protocol = {

      val f1 = strict[A]("in1")
      val f2 = strict[B]("in2")
      val f3 = strict[C]("in3")
      val f4 = strict[D]("in4")

      inner.specify4(name, f1, f2, f3, f4, Type[O])
    }

    def define5[A: TypeTag, B: TypeTag, C: TypeTag, D: TypeTag, E: TypeTag, O: TypeTag](name: String): Protocol = {

      val f1 = strict[A]("in1")
      val f2 = strict[B]("in2")
      val f3 = strict[C]("in3")
      val f4 = strict[D]("in4")
      val f5 = strict[E]("in5")

      inner.specify5(name, f1, f2, f3, f4, f5, Type[O])
    }

  }

}
