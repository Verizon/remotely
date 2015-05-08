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
package examples

import java.net.InetSocketAddress
import remotely.transport.netty.NettyTransport
import scalaz.concurrent.Task
import remotely.codecs._
import scalaz.stream._
import scodec.bits.ByteVector
import scala.reflect.runtime.universe._

object Streaming {

  // on server, populate environment with codecs and values
  val env = Environment.empty
    .codec[Int]
    .populate { _
    .declareStream("download", (n: Int) => Response.now { Process[Int](1,2,3,4) } )
    .declareStream("continuous", (p: Process[Task, Int]) => Response.now { p.map(_ + 1)} )
    .declare("upload", (p: Process[Task, Int]) => Response.async(p.runLog.map(_.sum)))
  }

  val addr = new InetSocketAddress("localhost", 8083)

  // on client - create local, typed declarations for server
  // functions you wish to call. This can be code generated
  // from `env`, since `env` has name/types for all declarations!
  import Remote.implicits._

  // Let's no try and do this one right now
  //val upload = Remote.ref[ByteVector => Int]("upload")
  val download = Remote.ref[Int => Process[Task,Int]]("download")
  val upload = Remote.ref[Process[Task, Int] => Int]("upload")
  // Let's not try and do this one right now
  //val analyze = Remote.ref[ByteVector => Process[Task, String]]("analyze")
  val continuous = Remote.ref[Process[Task,Int] => Process[Task, Int]]("continuous")
}

object StreamingMain extends App {
  import Streaming.{env,addr,download, continuous, upload}
  import Remote.implicits._

  println(env)

  // create a server for this environment
  val server = env.serve(addr).run

  val transport = NettyTransport.single(addr).run
  val expr: Remote[Process[Task, Int]] = download(10)
  val loc: Endpoint = Endpoint.single(transport)
  val result: Process[Task, Int] = expr.run(loc).run

  val task = result.map(_.toString).to(io.stdOut).run

  val stream: Process[Task, Int] = Process(1,2,3,4)

  upload(stream).runWithoutContext(loc)

  val continuousResult = continuous(stream).run(loc).run

  val task1 = continuousResult.map(_.toString).to(io.stdOut).run

  Task.gatherUnordered(Seq(task,task1)).run
  transport.shutdown.run; server.run
}
