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
package example.benchmark

import collection.immutable.IndexedSeq
import codecs._
import scodec.{Codec, codecs => C}

case class LargeW(one: Int,
                  two: List[String],
                  three: String,
                  four: Map[String, String],
                  five: List[MediumW],
                  six: IndexedSeq[SmallW])

case class MediumW(ay: Int,
                   bee: String,
                   cee: List[SmallW],
                   dee: Option[Int])

case class SmallW(alpha: Map[String,String],
                  omega: List[String])

case class BigW(one: Int)

object protocol {
  
  implicit val smallWCodec: Codec[SmallW] =
    (map(utf8,utf8) ~~ list(utf8)).widenAs[SmallW](SmallW.apply, SmallW.unapply)
  
  implicit val mediumWCodec: Codec[MediumW] =
    (int32 ~~ utf8 ~~ list[SmallW] ~~ optional(int32)).widenAs(MediumW.apply, MediumW.unapply)
  
  implicit val largeWCodec: Codec[LargeW] =
    (int32 ~~ list(utf8) ~~ utf8 ~~ map(utf8,utf8) ~~ list[MediumW] ~~ indexedSeq[SmallW]).widenAs(LargeW.apply, LargeW.unapply)
  
  implicit val bigWCodec: Codec[BigW] =
    int32.widenOpt(BigW.apply, BigW.unapply)

  val definition =
    Protocol.empty
      .codec[LargeW]
      .codec[MediumW]
      .codec[SmallW]
      .codec[BigW]
      .specify1("identityLarge", Field.strict[LargeW]("in"), Field.strict[LargeW]("out"))
      .specify1[MediumW, MediumW]("identityMedium", Field.strict[MediumW]("in"), Field.strict[MediumW]("out"))
      .specify1[BigW, BigW]("identityBig", Field.strict[BigW]("int"), Field.strict[BigW]("out"))
}
