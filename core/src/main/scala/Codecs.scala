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

import shapeless.ops.hlist.Prepend
import shapeless.{HNil, HList, ::}

import scala.reflect.runtime.universe.TypeTag
import scodec.{Codec,Decoder,Encoder}
import scodec.codecs.byteAligned

case class Codecs[A <: HList](codecs: Map[String,Codec[Any]]) {
  def codec[C:TypeTag:Codec]: Codecs[C :: A] = {
    val name = Remote.toTag(implicitly[TypeTag[C]])
    this.copy(codecs = codecs + (name -> byteAligned(Codec[C].asInstanceOf[Codec[Any]])))
  }

  def ++[C <: HList](c: Codecs[C])(implicit prepend : Prepend[A, C]) : Codecs[prepend.Out] = Codecs[prepend.Out](codecs ++ c.codecs)

  def keySet = codecs.keySet

  def get(k: String): Option[Codec[Any]] = codecs.get(k)

  def pretty = "Codecs.empty\n  " + codecs.keySet.toList.sorted.map(d => s".codec[$d]").mkString("\n  ")

  override def toString = pretty
}

object Codecs {

  val empty: Codecs[HNil] = Codecs(Map("List[remotely.Signature]" -> codecs.set(Signature.signatureCodec).asInstanceOf[Codec[Any]]))
}
