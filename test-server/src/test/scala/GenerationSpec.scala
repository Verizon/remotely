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
import scalaz.concurrent.Task
import scalaz.stream.Process

class TestServerImpl extends TestGenerationServer {
  def foo = Response.delay(Foo(1))
  def fooId = (foo: Foo) => Response.now(foo)
  def foobar = (foo: Foo) => Response.now(Bar(foo.a))
  def bar = Response.delay(Bar(1))
  def streamBar = (foo: Foo, bar: Bar) => Response.now(Process(bar))
  def streamInStreamOut = (in2: Process[Task, List[Bar]], in3: Foo) => Response.now(in2.map(_.head))
}

class GenerationSpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {

  val addr = new java.net.InetSocketAddress("localhost", 9436)

  val server = new TestServerImpl

  val shutdown = server.environment.serve(addr).run

  val endpoint = Endpoint.single(NettyTransport.single(addr).run)

  
  behavior of "Test Generation Server"
  
  it should "work for a streaming result" in {
    import remotely.Remote.implicits._
    import GenerationTest._
    val stream = TestGenerationClient.streamBar(Foo(3), Bar(4)).run(endpoint).run
    stream.runLog.run shouldEqual(List(Bar(4)))
  }

  it should "work for streaming args and a streaming result" in {
    import remotely.Remote.implicits._
    import GenerationTest._
    import codecs.list
    val barStream: Process[Task, List[Bar]] = Process(List(Bar(1)))
    val stream = TestGenerationClient.streamInStreamOut(barStream, Foo(4)).run(endpoint).run
    stream.runLog.run shouldEqual(List(Bar(1)))
  }

  override def afterAll() {
    shutdown.run
  }
}

