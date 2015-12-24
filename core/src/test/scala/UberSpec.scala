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

import org.scalatest.matchers.{Matcher,MatchResult}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scalaz.concurrent.Task
import scalaz.stream.Process
import remotely.transport.netty.NettyTransport
import scala.concurrent.duration.DurationInt
import java.util.concurrent.Executors

class UberSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  behavior of "permutations"
  it should "work" in {
    val input: Process[Task,Int] = Process.emitAll(Vector(1,2,3))
    val permuted : IndexedSeq[Process[Task,Int]] = Endpoint.permutations(input).runLog.run
    permuted.size should be (6)
    val all = permuted.map(_.runLog.run).toSet
    all should be(Set(IndexedSeq(1,2,3), IndexedSeq(1,3,2), IndexedSeq(2,1,3), IndexedSeq(2,3,1), IndexedSeq(3,1,2), IndexedSeq(3,2,1)))
  }

  behavior of "isEmpty"
  it should "work" in  {
    val empty: Process[Task,Int] = Process.halt
    Endpoint.isEmpty(empty).run should be (true)

    val notEmpty: Process[Task,Int] = Process.emit(1)
    Endpoint.isEmpty(notEmpty).run should be (false)

    val alsoNot:  Process[Task,Int] = Process.eval(Task.now(1))
    Endpoint.isEmpty(alsoNot).run should be (false)
  }

  behavior of "transpose"
  it should "work" in {
    val input = IndexedSeq(IndexedSeq("a", "b", "c"),IndexedSeq("q", "w", "e"), IndexedSeq("1", "2", "3"))
    val inputStream: Process[Task,Process[Task,String]] = Process.emitAll(input.map(Process.emitAll(_)))
    val transposed: IndexedSeq[IndexedSeq[String]] = Endpoint.transpose(inputStream).runLog.run.map(_.runLog.run)
    transposed should be (input.transpose)
  }

  val addr1 = new java.net.InetSocketAddress("localhost", 9000)
  val addr2 = new java.net.InetSocketAddress("localhost", 9009)

  val server1 = new CountServer
  val server2 = new CountServer
  val shutdown1 = server1.environment.serve(addr1).run
  val shutdown2 = server2.environment.serve(addr2).run

  override def afterAll() {
    shutdown1.run
    shutdown2.run
  }

  val endpoint1 = (NettyTransport.single(addr1) map Endpoint.single).run
  val endpoint2 = (NettyTransport.single(addr2) map Endpoint.single).run
  def endpoints: Process[Nothing,Endpoint] = Process.emitAll(List(endpoint1, endpoint2)) ++ endpoints
  val endpointUber = Endpoint.uber(1 second, 10 seconds, 10, endpoints)

  behavior of "uber"
  it should "work" in {
    import Response.Context
    import Remote.implicits._
    import codecs._
    val call = evaluate(endpointUber, Monitoring.empty)(CountClient.ping(1))

    val i: Int = call.apply(Context.empty).run
    val j: Int = call.apply(Context.empty).run
    j should be (2)
  }
}
