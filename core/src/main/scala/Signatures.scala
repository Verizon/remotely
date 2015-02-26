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
import scodec.{Codec,Encoder,Err}
import scodec.bits.BitVector
import scalaz.{\/,Foldable,NonEmptyList}
import scalaz.std.list._
import scalaz.std.string._
import shapeless._

case class Signature(name: String, tag: String, inTypes: List[String], outType: String) {
  /** returns a string in the form "Type, Type => Response[Type]",
    *  wrapping Response around the return type def
    */

  def wrapResponse: String = {
    val resp = s"Response[$outType]"
    inTypes match {
      case Nil => resp
      case h =>
        val in = Foldable[List].intercalate(inTypes, ",")
        s"$in => $resp"
    }
  }

}

object Signature {
  implicit val signatureCodec: Codec[Signature] = (utf8 ~~ utf8 ~~ list(utf8) ~~ utf8).widenAs[Signature](Signature.apply, Signature.unapply)
}

case class Signatures(signatures: Set[Signature]) {

  def specify0[A: TypeTag](name: String): Signatures =
    Signatures(signatures + Signature(name, Remote.nameToTag[A](name), List(), Remote.toTag[A]))

  def specify1[A:TypeTag,B:TypeTag](name: String): Signatures =
    Signatures(signatures + Signature(name, Remote.nameToTag[A=>B](name), List(Remote.toTag[A]), Remote.toTag[B]))

  def specify2[A:TypeTag,B:TypeTag,C:TypeTag](name: String): Signatures =
    Signatures(signatures + Signature(name, Remote.nameToTag[(A,B)=>C](name), List(Remote.toTag[A], Remote.toTag[B]), Remote.toTag[C]))

  def specify3[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag](name: String): Signatures =
    Signatures(signatures + Signature(name, Remote.nameToTag[(A,B,C)=>D](name), List(Remote.toTag[A], Remote.toTag[B], Remote.toTag[C]), Remote.toTag[D]))

  def specify4[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag](name: String): Signatures =
    Signatures(signatures + Signature(name, Remote.nameToTag[(A,B,C,D)=>D](name), List(Remote.toTag[A], Remote.toTag[B], Remote.toTag[C], Remote.toTag[D]), Remote.toTag[E]))

  def pretty: String = "Signatures.empty\n" +
  signatures.map(s => Signatures.split(s.tag) match { case (name,tname) =>
                   s"""  .specify[$tname]("$name")"""
                 }).mkString("\n")

}

object Signatures {
  val Arrow = "(.*)=>(.*)".r
  private[remotely] def wrapResponse(typename: String): String = typename match {
    case Arrow(l,r) => s"$l=> Response[${r.trim}]"
    case _ => s"Response[$typename]"
  }

  val empty = Signatures(Set(Signature("describe", "describe: List[remotely.Signature]", List(), "List[Remotely.Signature]")))

  // converts "sum: List[Int] => Int" to ("sum", "List[Int] => Int")
  private[remotely] def split(typedName: String): (String,String) = {
    val parts = typedName.split(':').toIndexedSeq
    val name = parts.init.mkString(":").trim
    val typename = parts.last.trim
    (name, typename)
  }

  private[remotely] def indent(by: String)(s: String): String =
    by + s.replace("\n", "\n" + by)
}
