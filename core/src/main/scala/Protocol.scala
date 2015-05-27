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

import shapeless._
import shapeless.ops.hlist.Selector

import scala.reflect.runtime.universe.TypeTag
import scodec.Codec

case class Protocol[H <: HList](codecs: Codecs[H], signatures: Signatures) {

  def codec[A:TypeTag:Codec]: Protocol[A :: H] =
    this.copy(codecs = codecs.codec[A])

  def specify0[O](name: String, out: Type[O])(implicit evidenceA : Selector[H, O]): Protocol[H] =
    this.copy(signatures = signatures.specify0(name, out))

  def specify1[A,O](name: String, in: Field[A], out: Type[O])(implicit evidenceA : Selector[H, A], evidenceB : Selector[H, O]): Protocol[H] =
    this.copy(signatures = signatures.specify1(name, in, out))

  def specify2[A,B,O](name: String, in1: Field[A], in2: Field[B], out: Type[O])(implicit evidenceA : Selector[H, A], evidenceB : Selector[H, B], evidenceC : Selector[H, O]): Protocol[H] =
    this.copy(signatures = signatures.specify2(name, in1, in2, out))

  def specify3[A,B,C,O](name: String, in1: Field[A], in2: Field[B], in3: Field[C], out: Type[O])(implicit evidenceA : Selector[H, A], evidenceB : Selector[H, B], evidenceC : Selector[H, C], evidenceD: Selector[H, O]): Protocol[H] =
    this.copy(signatures = signatures.specify3(name, in1, in2, in3, out))

  def specify4[A,B,C,D,O](name: String, in1: Field[A], in2: Field[B], in3: Field[C], in4: Field[D], out: Type[O])(implicit evidenceA : Selector[H, A], evidenceB : Selector[H, B], evidenceC : Selector[H, C], evidenceD: Selector[H, D], evidenceE: Selector[H, O]): Protocol[H] =
    this.copy(signatures = signatures.specify4(name, in1, in2, in3, in4, out))

  def pretty: String =
    "Protocol(\n" +
    Signatures.indent("  ")(codecs.pretty) + ",\n" +
    Signatures.indent("  ")(signatures.pretty) + "\n)"

  override def toString = pretty
}

object Protocol {

  val empty = Protocol(Codecs.empty, Signatures.empty)
}
