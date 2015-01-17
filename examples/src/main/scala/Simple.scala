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
import scalaz.concurrent.{Strategy,Task}
import codecs._

object Simple {

  def foo(i: Int): String = "BONUS"

  // on server, populate environment with codecs and values
  val env = Environment.empty
    .codec[Int]
    .codec[String]
    .codec[Double]
    .codec[Float]
    .codec[List[Int]]
    .codec[List[String]].populate { _
      .declareStrict("sum", (d: List[Int]) => d.sum )
      .declare("fac", (n: Int) => Response.delay { (1 to n).foldLeft(1)(_ * _)} ) // async functions also work
      .declareStrict("foo", foo _ ) // referencing existing functions works, too
    }

  val addr = new InetSocketAddress("localhost", 8083)

  // on client - create local, typed declarations for server
  // functions you wish to call. This can be code generated
  // from `env`, since `env` has name/types for all declarations!
  import Remote.implicits._

  val fac = Remote.ref[Int => Int]("fac")
  val gcd = Remote.ref[(Int,Int) => Int]("gcd")
  val sum = Remote.ref[List[Int] => Int]("sum")

  // And actual client code uses normal looking function calls
  val ar = fac(9)
  val ar1 = gcd(1, 2)
  val ar3 = sum(List(0,1,2,3,4))
  val ar2: Remote[Int] = ar
  val r: Remote[Int] = ar3
}

object SimpleMain extends App {
  import Simple.{env,addr,sum}
  import Remote.implicits._

  println(env)

  // create a server for this environment
  val server = env.serveNetty(addr, monitoring = Monitoring.consoleLogger("[server]"))

  val transport = NettyTransport.single(addr)
  val expr: Remote[Int] = sum(List(0,1,2,3,4))
  val loc: Endpoint = Endpoint.single(transport)
  val result: Task[Int] = expr.runWithContext(loc, Response.Context.empty, Monitoring.consoleLogger("[client]"))

  // running a couple times just to see the latency improve for subsequent reqs
  try println { result.run; result.run; result.run }
  finally {
    transport.shutdown.run
    server.run
  }
}
