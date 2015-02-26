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

import scodec._
import scodec.bits.BitVector
import scala.util.parsing.combinator._

/**
  * A Set of capabilities that a server possesses.
  * 
  * this set of capabilities will be transmitted from Server to client
  * at the time when a connection is built.
  * 
  * Currently there is only one known capability, which is the
  * "Remotely 1.0" capability.
  * 
  * You can see this if you telnet to a running remotely server:
  * {{{
      $ telnet localhost 9999
      Trying 127.0.0.1...
      Connected to localhost.
      Escape character is '^]'.
      OK: [Remotely 1.0]
  * }}}
  * 
  * The OK line spit out by the server contains a comma separated list
  * of capabilities between the square brackets.
  * 
  * In the future we might revision our wire format, perhaps because
  * we want to also allow for XML serialization of objects. Perhaps we
  * want to add STARTTLS support, perhaps we want to add a way to
  * fetch documentation for the functions exported by this server,
  * someday in the future, a remotely server might respond with:
  * 
  * OK: [Remotely 1.0, STARTTLS, Remotely XML 1.0, Remotely 2.0]
  * 
  * When the client receives this string as part of the connection
  * negotiation, it can perhaps adapt its behavior depending on the
  * server capabilities, or it could decide not to talk to this
  * server.
  */
case class Capabilities(capabilities: Set[String])

object Capabilities extends RegexParsers {

  /**
    * The Remotely 1.0 capability states that the server will have a
    * `describe` method, which can list all the available functions on
    * the server, and implies our current serialization format (which
    * is likely to change)
    */
  val `remotely-1.0` = "Remotely 1.0"

  val required: Set[String] = Set(`remotely-1.0`)

  val default = Capabilities(required)

  val capability: Parser[String] = "[^,\\]]*".r

  val capabilitiesList: Parser[List[String]] =
    '[' ~ repsep(capability, ',') ~ ']' ^^ { case _ ~ c ~ _ => c }

  val helloString: Parser[Capabilities] =
    "OK: " ~ capabilitiesList ^^ { case _ ~ c => Capabilities(c.toSet) }

  def parseHelloString(str: String): Attempt[Capabilities] = {
    val pr = parseAll(helloString, str)
    if(pr.successful) Attempt.successful(pr.get)
    else Attempt.failure(Err("could not parse greeting from server: " + str))
  }

  val capabilitiesCodec: Codec[Capabilities] = new Codec[Capabilities] {

    def sizeBound = SizeBound.unknown
    
    override def encode(c: Capabilities): Attempt[BitVector] = {
      val s = "OK: " + c.capabilities.mkString("[",",","]") + "\n"
      Attempt.successful(BitVector(s.getBytes))
    }

    override def decode(bv: BitVector): Attempt[DecodeResult[Capabilities]] = {
      codecs.utf8.decode(bv).flatMap {
        case DecodeResult(s, b) ⇒ parseHelloString(s).map(DecodeResult(_, b))
      }
    }
    
  }
  
}
