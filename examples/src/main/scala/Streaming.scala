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

  def foo(i: Int): String = "BONUS"

  // on server, populate environment with codecs and values
  val env = Environment.empty
    .codec[Int]
    .codec[String]
    .codec[Double]
    .codec[Float]
    .codec[List[Int]]
    .codec[List[String]].populate { _
    // It would be nice if this could fail to compile...
    .declare("download", (n: Int) => Response.now { Process[Byte](1,2,3,4) } )
    .declare("continuous", (p: Process[Task, Byte]) => Response.stream { p.map(_ + 1)} )
  }

  val addr = new InetSocketAddress("localhost", 8083)

  // on client - create local, typed declarations for server
  // functions you wish to call. This can be code generated
  // from `env`, since `env` has name/types for all declarations!
  import Remote.implicits._

  // Let's no try and do this one right now
  //val upload = Remote.ref[ByteVector => Int]("upload")
  val download = Remote.ref[Int => Process[Task,Byte]]("download")
  // Let's not try and do this one right now
  //val analyze = Remote.ref[ByteVector => Process[Task, String]]("analyze")
  val continuous = Remote.ref[Process[Task,Byte] => Process[Task, Byte]]("continuous")

  // And actual client code uses normal looking function calls
  val bytes = ByteVector(4,3,2,1)
  //val ar = upload(localToRemote(bytes)(scodec.codecs.bytes, implicitly[TypeTag[ByteVector]]))
  //val ar1 = analyze(localToRemote(bytes)(scodec.codecs.bytes, implicitly[TypeTag[ByteVector]]))
  val ar3 = download.apply(localToRemote(10)(scodec.codecs.int(8), implicitly[TypeTag[Int]]))
  //val ar2: Remote[Int] = ar
  val r: Remote[Process[Task, Byte]] = ar3
}

object StreamingMain extends App {
  import Streaming.{env,addr,download, continuous}
  import Remote.implicits._

  println(env)

  // create a server for this environment
  val server = env.serve(addr, monitoring = Monitoring.consoleLogger("[server]")).run

  val transport = NettyTransport.single(addr).run
  val expr: Remote[Process[Task, Byte]] = download(10)
  val loc: Endpoint = Endpoint.single(transport)
  val result: Process[Task, Byte] = expr
    .runWithContext(loc, Response.Context.empty, Monitoring.consoleLogger("[client]"))(implicitly[TypeTag[Byte]], scodec.codecs.byte)

  val task = result.map(_.toString).to(io.stdOut).run

  val byteStream: Process[Task, Byte] = Process(1,2,3,4)

  //upload.stream(byteStream).runWithContext(loc, Response.Context.empty, Monitoring.consoleLogger("[client]"))

  val continuousResult = continuous(byteStream)
    .runWithContext(loc, Response.Context.empty, Monitoring.consoleLogger("[client]"))(implicitly[TypeTag[Byte]], scodec.codecs.byte)

  val task1 = continuousResult.map(_.toString).to(io.stdOut).run

  Task.gatherUnordered(Seq(task,task1)).runAsync{_ => transport.shutdown.run; server.run}
}
