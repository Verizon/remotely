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

case class Codecs(codecs: Map[String,Codec[Any]]) {
  def codec[A:TypeTag:Codec]: Codecs = {
    val name = Remote.toTag(implicitly[TypeTag[A]])
    this.copy(codecs = codecs + (name -> Codec[A].asInstanceOf[Codec[Any]]))
  }


  def ++(c: Codecs): Codecs = Codecs(codecs ++ c.codecs)

  def keySet = codecs.keySet

  def get(k: String): Option[Codec[Any]] = codecs.get(k)

  def pretty =
    "Codecs.empty\n  " + codecs.keySet.toList.sorted.map(d => s".codec[$d]").mkString("\n  ")

  override def toString = pretty
}

object Codecs {

  val empty = Codecs(Map("List[remotely.Signature]" -> codecs.list(Signature.signatureCodec).asInstanceOf[Codec[Any]]))
}
