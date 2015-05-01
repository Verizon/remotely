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

import org.scalatest.{Matchers, FlatSpec}
import remotely.Remote.implicits._
import remotely.transport.netty.NettyTransport
import remotely.codecs._

import scalaz.concurrent.Task
import scalaz.stream._

import scalaz.stream.async
import utils._
import scala.concurrent.duration._

class StreamingSpec extends FlatSpec with Matchers {
  behavior of "Streaming"
  it should "work" in {
    // on server, populate environment with codecs and values
    val env = Environment.empty
      .codec[Byte]
      .codec[Int]
      .populate { _
      // It would be nice if this could fail to compile...
      .declare("download", (n: Int) => Response.stream { Process[Byte](1,2,3,4) } )
      .declare("continuous", (p: Process[Task, Byte]) => Response.stream { p.map(_ + 1)} )
    }

    val addr = new InetSocketAddress("localhost", 8083)

    val download = Remote.ref[Int => Process[Task,Byte]]("download")

    val continuous = Remote.ref[Process[Task,Byte] => Process[Task, Byte]]("continuous")

    val serverShutdown = env.serve(addr, monitoring = Monitoring.consoleLogger("[server]")).run

    val transport = NettyTransport.single(addr).run
    val expr: Remote[Process[Task, Byte]] = download(10)
    val loc: Endpoint = Endpoint.single(transport)
    val result: Process[Task, Byte] = expr.run(loc, M = Monitoring.consoleLogger("[client]"))

    val byteStream: Process[Task, Byte] = Process(3,4,5)

    val continuousResult = continuous(byteStream).run(loc, M = Monitoring.consoleLogger("[client]"))

    //continuousResult.map(_.toString).to(io.stdOut)

    continuousResult.runLog.run shouldEqual(List(4,5,6))

    transport.shutdown.run
    serverShutdown.run
  }
  ignore should "work (mutable)" in {
    // on server, populate environment with codecs and values
    val env = Environment.empty
      .codec[Byte]
      .codec[Int]
      .populate { _
      // It would be nice if this could fail to compile...
      .declare("download", (n: Int) => Response.stream { Process[Byte](1,2,3,4) } )
      .declare("continuous", (p: Process[Task, Byte]) => Response.stream { p.map(_ + 1)} )
    }

    val addr = new InetSocketAddress("localhost", 8083)

    val download = Remote.ref[Int => Process[Task,Byte]]("download")

    val continuous = Remote.ref[Process[Task,Byte] => Process[Task, Byte]]("continuous")

    val serverShutdown = env.serve(addr, monitoring = Monitoring.consoleLogger("[server]")).run

    val transport = NettyTransport.single(addr).run
    val expr: Remote[Process[Task, Byte]] = download(10)
    val loc: Endpoint = Endpoint.single(transport)
    val result: Process[Task, Byte] = expr.run(loc, M = Monitoring.consoleLogger("[client]"))

    val q = async.unboundedQueue[Byte]
    val byteStream = q.dequeue

    //upload.stream(byteStream).runWithContext(loc, Response.Context.empty, Monitoring.consoleLogger("[client]"))

    val continuousResult = continuous(byteStream).run(loc, M = Monitoring.consoleLogger("[client]"))

    continuousResult.map(_.toString).to(io.stdOut)

    q.enqueue(9)
    //continuousResult(0).timed(1.second).run shouldEqual(10)

    q.enqueue(19)
    //continuousResult(1).timed(1.second).run shouldEqual(20)

    q.enqueue(29)
    //continuousResult(2).timed(1.second).run shouldEqual(30)

    transport.shutdown.run
    serverShutdown.run
  }
}