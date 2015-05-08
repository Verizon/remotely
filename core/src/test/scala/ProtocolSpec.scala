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

import collection.immutable.SortedSet
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import codecs._
import Response.Context
import transport.netty._

class ProtocolSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  val addr = new java.net.InetSocketAddress("localhost", 9002)
  val server = new TestServer
  val shutdown = server.environment.serve(addr).run

  val endpoint = (NettyTransport.single(addr, monitoring = Monitoring.consoleLogger("ProtocolSpec")) map Endpoint.single).run

  it should "foo" in {
    import remotely.Remote.implicits._
    val fact: Int = Client.factorial(10).runWithoutContext(endpoint, Monitoring.consoleLogger()).run
    val lst: List[Int] = Client.foo(9).runWithContext(endpoint, Context.empty).run
  }

  override def afterAll(){
    Thread.sleep(500)
    shutdown.run
  }
}

trait TestServerBase {

  import Codecs._

  def environment = Environment(
    Codecs.empty
      .codec[Int]
      .codec[List[Int]]
      .codec[List[Signature]],
    populateDeclarations(Values.empty)
  )

  def factorial: Int => Response[Int]
  def foo: Int => Response[scala.List[Int]]
  def describe: Response[scala.List[Signature]]

  private def populateDeclarations(env: Values): Values = env
    .declare("factorial", factorial)
    .declare("foo", foo)
    .declare("describe", describe)
}

class TestServer extends TestServerBase {
  implicit val intcodec = int32
  def factorial: Int => Response[Int] = i => Response.now(i * i)
  def foo: Int => Response[List[Int]] = i =>  {
    Response.now(collection.immutable.List.fill(10000)(i))
  }
  def describe: Response[scala.List[Signature]] = Response.now(List(Signature("factorial", "factorial: Int => Int", List("Int"), "Int", false),Signature("foo", "foo: Int => List[Int]", List("Int"), "List[Int]", false),Signature("describe", "describe: List[Signature]", Nil, "List[Signature]", false)))
}

object Client {
  val factorial = Remote.ref[Int => Int]("factorial")
  val foo = Remote.ref[Int => scala.List[Int]]("foo")
}
