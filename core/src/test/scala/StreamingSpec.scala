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

import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpec}
import remotely.Remote.implicits._
import remotely.transport.netty.NettyTransport
import remotely.codecs._

import scalaz.concurrent.Task
import scalaz.stream._

import scalaz.stream.async

class StreamingSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  behavior of "Streaming"
  // on server, populate environment with codecs and values
  val env = Environment.empty
    .codec[Byte]
    .codec[Int]
    .populate { _
    // It would be nice if this could fail to compile...
    .declareStream("download", (n: Int) => Response.now { Process[Byte](1,2,3,4) } )
    .declareStream("continuous", (p: Process[Task, Int]) => Response.now { p.map(_ + 1)} )
  }

  val addr = new InetSocketAddress("localhost", 8091)

  val download = Remote.ref[Int => Process[Task,Byte]]("download")

  val continuous = Remote.ref[Process[Task,Int] => Process[Task, Int]]("continuous")

  val serverShutdown = env.serve(addr, monitoring = Monitoring.consoleLogger("[server]")).run

  val transport = NettyTransport.single(addr).run
  val loc: Endpoint = Endpoint.single(transport)

  ignore should "work for a function that returns a Stream" in {
    val expr: Remote[Process[Task, Byte]] = download(10)
    val result: Process[Task, Byte] = expr.run(loc, M = Monitoring.consoleLogger("[client]")).run

    result.runLog.run shouldEqual(Seq(1,2,3,4))
  }
  it should "work for a function that takes a stream and returns a Stream" in {
    val byteStream: Process[Task, Int] = Process(3,4)

    val continuousResult = continuous.apply(byteStream).run(loc, M = Monitoring.consoleLogger("[client]")).run

    continuousResult.runLog.run shouldEqual(List(4,5,6))
  }
  ignore should "work (mutable)" in {
    val q = async.unboundedQueue[Int]
    val byteStream = q.dequeue

    //upload.stream(byteStream).runWithContext(loc, Response.Context.empty, Monitoring.consoleLogger("[client]"))

    val continuousResult = continuous(byteStream).run(loc, M = Monitoring.consoleLogger("[client]")).run

    continuousResult.map(_.toString).to(io.stdOut)

    q.enqueueOne(9).run
    //continuousResult(0).timed(1.second).run shouldEqual(10)

    q.enqueueOne(19).run
    //continuousResult(1).timed(1.second).run shouldEqual(20)

    q.enqueueOne(29).run
    //continuousResult(2).timed(1.second).run shouldEqual(30)
  }

  override def afterAll() = {
    transport.shutdown.run
    serverShutdown.run
  }
}