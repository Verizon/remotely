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
package server
package test

import org.scalatest.matchers.{Matcher,MatchResult}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import remotely.ServerException
import remotely.transport.netty._
import remotely.{Monitoring,Response,Endpoint,codecs}, codecs._, Response.Context
import scala.collection.immutable.IndexedSeq
import scalaz.{-\/,\/-}
import scalaz.concurrent.{Strategy,Task}
import remotely._
import java.util.concurrent._
import protocol._

class BenchmarkServerSpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with transformations {

  val addr = new java.net.InetSocketAddress("localhost", 9001)
  val server = new BenchmarkServerImpl
  val shutdown: Task[Unit] = server.environment.serveNetty(addr, Strategy.DefaultStrategy, Monitoring.consoleLogger())

  val endpoint = Endpoint.single(NettyTransport.single(addr))

  import remotely.Remote.implicits._
  import remotely.codecs._

  override def afterAll(){
    Thread.sleep(500)
    shutdown.run
  }

  behavior of "identityBig"
  it should "work" in {
    val big = Big(1)
    val res = BenchmarkClient.identityBig(toBigW(big)).runWithoutContext(endpoint).run

    fromBigW(res) should equal (big)
  }
  behavior of "identityLarge"
  it should "serialize" in {
    val small = Small(Map.empty, List("asdf"))
    val big = Large(1, Nil, "string2", Map("key" -> "value"), List(Medium(2, "string", List(small), None)), Vector(small))
    fromLargeW(toLargeW(big)) should equal (big)
  }

  it should "work" in {
    val small = Small(Map.empty, List("asdf"))
    val big = Large(1, Nil, "string2", Map("key" -> "value"), List(Medium(2, "string", List(small), None)), Vector(small))
    val res = BenchmarkClient.identityLarge(toLargeW(big)).runWithoutContext(endpoint).run

    fromLargeW(res) should equal (big)
  }
}
