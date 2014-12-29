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
