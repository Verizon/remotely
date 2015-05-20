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
    Signature("foo",List(), "Baz", false).wrapResponse should be ("Response[Baz]")
    Signature("foo", List(Field("baz", "Baz")), "Qux", false).wrapResponse should be ("Baz => Response[Qux]")
    Signature("foo", List(Field("baz", "Baz"), Field("qux", "Qux"), false), "Zod").wrapResponse should be ("Baz,Qux => Response[Zod]")
  }
}
