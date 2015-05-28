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
package test

import scodec.Codec
import codecs.list

object GenerationTest {
  implicit lazy val fooCodec: Codec[Foo] = codecs.int32.as[Foo]
  implicit lazy val barCodec: Codec[Bar] = codecs.int32.as[Bar]
  implicit lazy val sigCodec: Codec[List[Signature]] = codecs.list(Signature.signatureCodec)

  val definition = Protocol.empty
    .codec[Foo]
    .codec[Bar]
    .codec[List[Bar]]
    .specify0("foo", Type.strict[Foo])
    .specify1("fooId", Field.strict[Foo]("in"), Type.strict[Foo])
    .specify1("foobar", Field.strict[Foo]("in"), Type.strict[Bar])
    .specify2("streamBar", Field.strict[Foo]("in1"), Field.strict[Bar]("in2"), Type.stream[Bar])
    .specify2("streamInStreamOut", Field.stream[List[Bar]]("in2"), Field.strict[Foo]("in3"), Type.stream[Bar])
}

