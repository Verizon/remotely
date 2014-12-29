package remotely
package test

import org.scalatest.matchers.{Matcher,MatchResult}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}

class SignatureSpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {
  
  behavior of "Signature"
  
  it should "be able to wrap a response type" in {
    Signature("foo", "bar", List(), "baz").wrapResponse should be ("Response[baz]")
    Signature("foo", "bar", List("baz"), "qux").wrapResponse should be ("baz => Response[qux]")
    Signature("foo", "bar", List("baz", "qux"), "zod").wrapResponse should be ("baz,qux => Response[zod]")
  }
}
