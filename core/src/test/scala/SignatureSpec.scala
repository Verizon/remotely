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

import org.scalatest.matchers.{Matcher,MatchResult}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}

class SignatureSpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {
  
  behavior of "Signature"
  
  it should "be able to wrap a response type" in {
    Signature("foo",List(), Type("Baz", isStream = false)).wrapResponse should be ("Response[Baz]")
    Signature("foo", List(Field("baz", Type("Baz", isStream = false))), Type("Qux", isStream = false)).wrapResponse should be ("Baz => Response[Qux]")
    Signature("foo", List(Field("baz", Type("Baz", isStream = false)), Field("qux", Type("Qux", isStream = false))), Type("Zod", isStream = false)).wrapResponse should be ("(Baz,Qux) => Response[Zod]")

  }

  it should "produce the correct typeString" in {
    Signature("foo", List(Field("baz", Type("Baz", isStream = true))), Type("Zod", isStream = false)).typeString should be ("scalaz.stream.Process[scalaz.concurrent.Task,Baz] => Zod")
    val sig = Signature("foo", List(
      Field("in1", Type("Baz", isStream = true)),
      Field("in2", Type("List[Barn]", isStream = true)),
      Field("in3", Type("Biz", isStream = false))
    ), Type("Zod", isStream = true))
    sig.typeString should be ("(scalaz.stream.Process[scalaz.concurrent.Task,Baz],scalaz.stream.Process[scalaz.concurrent.Task,List[Barn]],Biz) => scalaz.stream.Process[scalaz.concurrent.Task,Zod]")
  }
}
