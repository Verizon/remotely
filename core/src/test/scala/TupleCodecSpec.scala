package remotely
package test

import org.scalacheck._
import Arbitrary._
import org.scalacheck.Prop.forAll
import remotely.codecs._
import scalaz.{\/,-\/,\/-}
import scodec.bits.BitVector

object TupleCodecSpec extends Properties("TupleCodec") {
  property("tupele2Codec works") = forAll {ab: (String,List[Int]) ⇒
    val abCodec = utf8 ~~ list(int32)
    val roundTripped = abCodec.encode(ab) flatMap (abCodec.decode _)
    roundTripped == \/-(BitVector.empty -> ab)
  }
}
