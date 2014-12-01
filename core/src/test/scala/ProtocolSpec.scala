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
import java.util.concurrent.Executors
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import codecs._, Response.Context
import codecs._
import transport.netty._

class ProtocolSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  val addr = new java.net.InetSocketAddress("localhost", 9000)
  val server = new TestServer
  val shutdown: () => Unit = server.environment.serveNetty(addr, Executors.newCachedThreadPool)(Monitoring.empty)

  val endpoint = Endpoint.single(NettyTransport.single(addr))

  it should "foo" in {
    import remotely.Remote.implicits._
    val fact: Int = evaluate(endpoint, Monitoring.consoleLogger())(Client.factorial(10)).apply(Context.empty).run
    val lst: List[Int] = Client.foo(9).runWithContext(endpoint, Context.empty).run
  }

  override def afterAll(){
    Thread.sleep(500)
    shutdown()
  }
}

trait TestServerBase {

  import Codecs._

  def environment: Environment = Environment(
    Codecs.empty
      .codec[Int]
      .codec[List[Int]],
    populateDeclarations(Values.empty)
  )

  def factorial: Int => Response[Int]
  def foo: Int => Response[scala.List[Int]]

  private def populateDeclarations(env: Values): Values = env
    .declare("factorial", factorial)
    .declare("foo", foo)
}

class TestServer extends TestServerBase {
  implicit val intcodec = int32
  def factorial: Int => Response[Int] = i => Response.now(i * i)
  def foo: Int => Response[List[Int]] = i =>  {
    Response.now(collection.immutable.List.fill(10000)(i))
  }
}

object Client {
  val factorial = Remote.ref[Int => Int]("factorial")
  val foo = Remote.ref[Int => scala.List[Int]]("foo")
}
