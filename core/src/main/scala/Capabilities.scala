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

import scodec.{Codec,Err}
import scodec.bits.BitVector
import scalaz.{-\/,\/,\/-}
import scala.util.parsing.combinator._

case class Capabilities(capabilities: Set[String])

object Capabilities extends RegexParsers {
  val required: Set[String] = Set("Remotely 1.0")
  val default = Capabilities(required)

  val capability: Parser[String] = "[^,\\]]*".r

  val capabilitiesList: Parser[List[String]] =
    '[' ~ repsep(capability, ',') ~ ']' ^^ { case _ ~ c ~ _ => c }

  val helloString: Parser[Capabilities] =
    "OK: " ~ capabilitiesList ^^ { case _ ~ c => Capabilities(c.toSet) }

  def parseHelloString(str: String): Err \/ Capabilities = {
    val pr = parseAll(helloString, str)
    if(pr.successful) \/-(pr.get)
    else -\/(Err("could not parse greeting from server: " + str))
  }

  val capabilitiesCodec: Codec[Capabilities] = new Codec[Capabilities] {
    override def encode(c: Capabilities): Err \/ BitVector = {
      val str = "OK: " + c.capabilities.mkString("[",",","]") + "\n"
      \/-(BitVector(str.getBytes))
    }


    override def decode(bv: BitVector): Err \/ (BitVector,Capabilities) = {
      codecs.utf8.decode(bv).flatMap { case(bv,str) => parseHelloString(str).map(bv -> _) }
    }
  }
}
