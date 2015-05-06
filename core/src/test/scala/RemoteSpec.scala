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

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.scalacheck._
import Prop._
import scalaz.concurrent.Task
import transport.netty._

object RemoteSpec extends Properties("Remote") {
  import codecs._
  import Remote.implicits._

  val env = Environment.empty
    .codec[Int]
    .codec[Double]
    .codec[List[Int]]
    .codec[List[Double]]
    .codec[List[Signature]]
    .populate { _
                 .declare("sum", (d: List[Int]) => Response.now(d.sum))
                 .declare("describe", Response.now(List(Signature("sum", "sum: List[Double] => Double", List("List[Double]"), "Double"),
                                                        Signature("sum", "sum: List[Int] => Int", List("List[Int]"), "Int"),
                                                        Signature("add1", "add1: List[Int] => List[Int]", List("List[Int]"), "List[Int]"),
                                                        Signature("describe", "describe: List[Signature]", Nil, "List[Signature]"))))
                                                                  
      .declare("sum", (d: List[Double]) => Response.now(d.sum))
      .declare("add1", (d: List[Int]) => Response.now(d.map(_ + 1):List[Int]))
    }

  val addr = new InetSocketAddress("localhost", 8082)
  val server = env.serve(addr).run
  val nettyTrans = NettyTransport.single(addr).run
  val loc: Endpoint = Endpoint.single(nettyTrans)

  val sum = Remote.ref[List[Int] => Int]("sum")
  val sumD = Remote.ref[List[Double] => Double]("sum")
  val mapI = Remote.ref[List[Int] => List[Int]]("add1")

  val ctx = Response.Context.empty

  property("roundtrip") =
    forAll { (l: List[Int], kvs: Map[String,String]) =>
      l.sum == sum(l).runWithoutContext(loc).run//(loc, ctx ++ kvs).run
    }

  property("roundtrip[Double]") =
    forAll { (l: List[Double], kvs: Map[String,String]) =>
      l.sum == sumD(l).runWithContext(loc, ctx ++ kvs).run
    }

  property("roundtrip[List[Int]]") =
    forAll { (l: List[Int], kvs: Map[String,String]) =>
      l.map(_ + 1) == mapI(l).runWithContext(loc, ctx ++ kvs).run
    }

  property("check-serializers") = secure {
    // verify that server returns a meaningful error when it asks for
    // decoder(s) the server does not know about
    val wrongsum = Remote.ref[List[Float] => Float]("sum")
    val t: Task[Float] = wrongsum(List(1.0f, 2.0f, 3.0f)).runWithContext(loc, ctx)
    t.attemptRun.fold(
      e => {
        println("test resulted in error, as expected:")
        println(prettyError(e.toString))
        true
      },
      a => false
    )
  }

  property("check-declarations") = secure {
    // verify that server returns a meaningful error when client asks
    // for a remote ref that is unknown
    val wrongsum = Remote.ref[List[Int] => Int]("product")
    val t: Task[Int] = wrongsum(List(1, 2, 3)).runWithContext(loc, ctx)
    t.attemptRun.fold(
      e => {
        println("test resulted in error, as expected:")
        println(prettyError(e.toString))
        true
      },
      a => false
    )
  }
/* These take forever on travis, and so I'm disabling them, we should leave benchmarking of scodec to scodec and handle benchmarking of remotely in the benchmarking sub-projects

  property("encoding speed") = {
    val N = 2000
    val M = 1024
    val ints = List.range(0, M)
    val c = scodec.Codec[List[Int]]
    val t = time {
      (0 until N).foreach { _ => c.encode(ints); () }
    }
    println { "took " + t / 1000.0 +"s to encode " + (M*N*4 / 1e6) + " MB" }
    true
  }

  property("decoding speed") = {
    val N = 2000
    val M = 1024
    val ints = List.range(0, M)
    val c = scodec.Codec[List[Int]]
    val bits = c.encodeValid(ints)
    val t = time {
      (0 until N).foreach { _ => c.decode(bits); () }
    }
    println { "took " + t / 1000.0 +"s to decode " + (M*N*4 / 1e6) + " MB" }
    true
  }

  property("round trip speed") = {
    val l: List[Int] = List(1)
    val N = 5000
    val t = time {
      (0 until N).foreach { _ => sum(l).runWithContext(loc, ctx).run; () }
    }
    println { "round trip took average of: " + (t/N.toDouble) + " milliseconds" }
    true
  }
 */

  // NB: this property should always appear last, so it runs after all properties have run
  property("cleanup") = lazily {
    server.run
    nettyTrans.pool.close()
    true
  }

  def time(a: => Unit): Long = {
    val start = System.currentTimeMillis
    a
    System.currentTimeMillis - start
  }

  def prettyError(msg: String): String = {
    msg.take(msg.indexOfSlice("stack trace:"))
  }

  def lazily(p: => Prop): Prop = {
    lazy val pe = secure { p }
    new Prop { def apply(p: Gen.Parameters) = pe(p) }
  }

}
