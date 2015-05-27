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
import codecs._
import scodec.Codec

case class Field[+A]private[remotely](name: String, type_ : Type[A])
object Field {
  def strict[A:TypeTag](name: String) = Field[A](name, Type(Remote.toTag[A], isStream = false))
  def stream[A:TypeTag](name: String) = Field[A](name, Type(Remote.toTag[A], isStream = true))
}
case class Type[+A]private[remotely](name: String, isStream: Boolean) {
  def pretty = if (isStream) s"Process[Task,$name]" else name
}
object Type {
  def strict[A:TypeTag]: Type[A] = Type[A](Remote.toTag[A], isStream = false)
  def stream[A:TypeTag]: Type[A] = Type[A](Remote.toTag[A], isStream = true)
}
case class Signature(name: String, params: List[Field[Any]], out: Type[Any]) {
  /** returns a string in the form "Type, Type => Response[Type]",
    *  wrapping Response around the return type def
    */
  def wrapResponse: String =
    s"${lhsWithArrow}Response[${out.pretty}]"

  private def lhs = params.map(_.type_.pretty).mkString("(", ",", ")")

  private def lhsWithArrow = if (params.isEmpty) "" else s"$lhs => "

  def typeString = lhsWithArrow + out.pretty

  def tag = {
    s"$name: $typeString"
  }

}

object Signature {
  // What is the difference between ~ and ~~ ?
  implicit val typeCodec: Codec[Type[Any]] = (utf8 ~~ bool).widenAs[Type[Any]](Type.apply, Type.unapply)
  implicit val fieldCodec: Codec[Field[Any]] = (utf8 ~~ typeCodec).widenAs[Field[Any]](Field.apply, Field.unapply)
  implicit val signatureCodec: Codec[Signature] = (utf8  ~~ list(fieldCodec) ~~ typeCodec).widenAs[Signature](Signature.apply, Signature.unapply)
}

case class Signatures(signatures: Set[Signature]) {

  def specify(name: String, in: List[Field[Any]], out: Type[Any]) =
    Signatures(signatures + Signature(name, in, out))

  def specify0(name: String, out: Type[Any]): Signatures =
    specify(name, Nil, out)

  def specify1(name: String, in: Field[Any], out: Type[Any]): Signatures =
    specify(name, List(in), out)

  def specify2(name: String, in1: Field[Any], in2: Field[Any], out: Type[Any]): Signatures =
    specify(name, List(in1, in2), out)

  def specify3(name: String, in1: Field[Any], in2: Field[Any], in3: Field[Any], out: Type[Any]): Signatures =
    specify(name, List(in1, in2, in3), out)

  def specify4(name: String, in1: Field[Any], in2: Field[Any], in3: Field[Any], in4: Field[Any], out: Type[Any]): Signatures =
    specify(name, List(in1, in2, in3, in4), out)

  def pretty: String = "Signatures.empty\n" +
                        signatures.map(s =>
                           s"""  .specify[${s.typeString}]("${s.name}")"""
                         ).mkString("\n")

}

object Signatures {
  val empty = Signatures(Set(Signature("describe", List(), Type("List[remotely.Signature]", isStream = false))))

  private[remotely] def indent(by: String)(s: String): String =
    by + s.replace("\n", "\n" + by)
}
