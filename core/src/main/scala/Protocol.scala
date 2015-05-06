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
import scodec.{Codec,Decoder,Encoder}

case class Protocol[H <: HList](codecs: Codecs[H], signatures: Signatures) {

  def codec[A:TypeTag:Codec]: Protocol[A :: H] =
    this.copy(codecs = codecs.codec[A])

  def specify0[A:TypeTag](name: String)(implicit evidenceA : Selector[H, A]): Protocol[H] =
    this.copy(signatures = signatures.specify0[A](name))

  def specify1[A:TypeTag,B:TypeTag](name: String)(implicit evidenceA : Selector[H, A], evidenceB : Selector[H, B]): Protocol[H] =
    this.copy(signatures = signatures.specify1[A,B](name))

  def specify2[A:TypeTag,B:TypeTag,C:TypeTag](name: String)(implicit evidenceA : Selector[H, A], evidenceB : Selector[H, B], evidenceC : Selector[H, C]): Protocol[H] =
    this.copy(signatures = signatures.specify2[A,B,C](name))

  def specify3[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag](name: String)(implicit evidenceA : Selector[H, A], evidenceB : Selector[H, B], evidenceC : Selector[H, C], evidenceD: Selector[H, D]): Protocol[H] =
    this.copy(signatures = signatures.specify3[A,B,C,D](name))

  def specify4[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag](name: String)(implicit evidenceA : Selector[H, A], evidenceB : Selector[H, B], evidenceC : Selector[H, C], evidenceD: Selector[H, D], evidenceE: Selector[H, E]): Protocol[H] =
    this.copy(signatures = signatures.specify4[A,B,C,D,E](name))

  def pretty: String =
    "Protocol(\n" +
    Signatures.indent("  ")(codecs.pretty) + ",\n" +
    Signatures.indent("  ")(signatures.pretty) + "\n)"

  override def toString = pretty
}

object Protocol {

  val empty = Protocol(Codecs.empty, Signatures.empty)
}
