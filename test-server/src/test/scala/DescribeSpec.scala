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
import scodec.Codec
import transport.netty._
import java.util.concurrent.Executors
import scalaz.-\/

trait ServerImpl {
  def foo = Response.delay(Foo(1))
  def fooId = (foo: Foo) => Response.now(foo)
  def foobar = (foo: Foo) => Response.now(Bar(foo.a))
  def bar = Response.delay(Bar(1))
}

class DescribeTestOlderServerImpl extends DescribeTestOlderServer with ServerImpl
class DescribeTestNewerServerImpl extends DescribeTestNewerServer with ServerImpl

class DescribeSpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {

  import DescribeTestNewerProtocol._

  val addrN = new java.net.InetSocketAddress("localhost", 9006)
  val addrO = new java.net.InetSocketAddress("localhost", 9007)

  val serverN = new DescribeTestNewerServerImpl
  val serverO = new DescribeTestOlderServerImpl

  val shutdownN: () => Unit = serverN.environment.serve(addrN,
                                                           Executors.newCachedThreadPool,
                                                           Monitoring.empty)

  val shutdownO: () => Unit = serverO.environment.serve(addrO,
                                                           Executors.newCachedThreadPool,
                                                           Monitoring.empty)

  val endpointOldToOld = Endpoint.single(NettyTransport.single(addrO, DescribeTestOlderClient.expectedSignatures, Monitoring.consoleLogger("OldToOld")))
  val endpointOldToNew = Endpoint.single(NettyTransport.single(addrN, DescribeTestOlderClient.expectedSignatures, Monitoring.consoleLogger("OldToNew")))
  val endpointNewToOld = Endpoint.single(NettyTransport.single(addrO, DescribeTestNewerClient.expectedSignatures, Monitoring.consoleLogger("NewToOld")))
  val endpointNewToNew = Endpoint.single(NettyTransport.single(addrN, DescribeTestNewerClient.expectedSignatures, Monitoring.consoleLogger("NewToNew")))
  
  behavior of "Describe"
  
  it should "work" in {
    import codecs.list
    import Signature._
    import remotely.Remote.implicits._
    val desc = evaluate(endpointNewToNew, Monitoring.consoleLogger())(DescribeTestNewerClient.describe).apply(Response.Context.empty).run
    desc should contain (Signature("foo", "foo: remotely.test.Foo", Nil, "remotely.test.Foo"))
    desc should contain (Signature("fooId", "fooId: remotely.test.Foo => remotely.test.Foo", List("remotely.test.Foo"), "remotely.test.Foo"))
    desc should contain (Signature("foobar", "foobar: remotely.test.Foo => remotely.test.Bar", List("remotely.test.Foo"), "remotely.test.Bar"))
    desc should contain (Signature("describe", "describe: List[remotely.Signature]", Nil, "List[Remotely.Signature]"))
  }

  behavior of "Client"

  it should "connect older to newer" in {
    val desc = evaluate(endpointOldToNew, Monitoring.consoleLogger())(DescribeTestOlderClient.describe).apply(Response.Context.empty).run
    desc should contain (Signature("foo", "foo: remotely.test.Foo", Nil, "remotely.test.Foo"))
  }

  it should "connect newer to newer" in {
    val desc = evaluate(endpointNewToNew, Monitoring.consoleLogger())(DescribeTestNewerClient.describe).apply(Response.Context.empty).run
    desc should contain (Signature("foo", "foo: remotely.test.Foo", Nil, "remotely.test.Foo"))
  }

  it should "not connect newer to older" in {
    val desc = evaluate(endpointNewToOld, Monitoring.consoleLogger())(DescribeTestNewerClient.describe).apply(Response.Context.empty).attemptRun
    println("new to old: "  + desc)
    desc match {
      case -\/(e) => e shouldBe a [IncompatibleServer]
      case e => withClue("newer client should have rejected older server")(fail())
    }
  }

  override def afterAll() {
    shutdownN()
    shutdownO()
  }
}

