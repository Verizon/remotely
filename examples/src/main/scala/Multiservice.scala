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

import codecs._
import Remote.implicits._
import java.util.concurrent.Executors
import scalaz.concurrent.Task
import transport.netty._

/**
 * This is a complete example of one service calling another service.
 * Service A exposes `sum`, `length`, and serivce B exposes `average`
 * by calling `sum` and `length` on Service A. A fresh id is generated
 * and pushed onto the trace stack for each nested remote request.
 */
object Multiservice extends App {

  // Define a service exposing `sum` and `length` functions for `List[Double]`
  val env1 = Environment.empty
    .codec[Double]
    .codec[List[Double]].populate { _
      .declare("sum", (xs: List[Double]) => Response.now(xs.sum))
      .declare("length", (xs: List[Double]) => Response.now(xs.length.toDouble))
      .declare("divide", (x: Double, y: Double) => Response.now(x / y))
    }

  // Manually generate the client API for this service
  val sum = Remote.ref[List[Double] => Double]("sum")
  val length = Remote.ref[List[Double] => Double]("length")
  val div = Remote.ref[(Double,Double) => Double]("divide")

  // if we really want, can give `div` infix syntax
  implicit class DivSyntax(r: Remote[Double]) {
    def /(r2: Remote[Double]): Remote[Double] = div(r,r2)
  }

  // Serve these functions
  val addr1 = new java.net.InetSocketAddress("localhost", 8080)
  val transport = NettyTransport.single(addr1)
  val stopA = env1.serveNetty(addr1, Executors.newCachedThreadPool)(Monitoring.consoleLogger("[service-a]"))

  // And expose an `Endpoint` for making requests to this service
  val serviceA: Endpoint = Endpoint.single(transport)

  // Define a service exposing an `average` function, which calls `serviceA`.
  val env2 = Environment.empty
    .codec[Double]
    .codec[List[Double]].populate { _
      // This version will make a single request to `serviceA`
      .declare("average2", (xs: List[Double]) => div(sum(xs), length(xs)).run(serviceA))
      // this version will make two requests to `serviceA`, and run them sequentially
      .declare("average", (xs: List[Double]) => for {
        sumR <- sum(xs).run(serviceA)
        countR <- length(xs).run(serviceA)
      } yield sumR / countR )
      // Using infix syntax
      .declare("average3", (xs: List[Double]) => (sum(xs) / length(xs)).run(serviceA))
      // This version will make three requests to `serviceA`, but will make
      // the first two requests (for the sum and count) in parallel
      .declare("average4", (xs: List[Double]) =>
        // The number of round trips is just the number of calls to run
        div(sum(xs).run(serviceA),
            length(xs).run(serviceA)).run(serviceA)
      )
      // This version checks the "flux-capacitor-status" key of the header
      .declare("average5", (xs: List[Double]) => for {
        ctx <- Response.ask
        avg <- if (ctx.header.contains("flux-capacitor")) {
                 println("Flux capacitor is enabled, calling service A in a single request!!")
                 (sum(xs) / length(xs)).run(serviceA)
               }
               else // okay, do the same thing anyway
                 (sum(xs) / length(xs)).run(serviceA)
      } yield avg)
    }

  // Manually generate the client API for this service
  val average = Remote.ref[List[Double] => Double]("average")
  val average2 = Remote.ref[List[Double] => Double]("average2")
  val average3 = Remote.ref[List[Double] => Double]("average3")
  val average4 = Remote.ref[List[Double] => Double]("average4")
  val average5 = Remote.ref[List[Double] => Double]("average5")

  // Serve these functions
  val addr2 = new java.net.InetSocketAddress("localhost", 8081)
  val stopB = env2.serveNetty(addr2, Executors.newCachedThreadPool)(Monitoring.consoleLogger("[service-b]"))
  val serviceB: Endpoint = Endpoint.single(NettyTransport.single(addr2))

  try {
    val ctx = Response.Context.empty ++ List("flux-capacitor" -> "great SCOTT!")
    val M = Monitoring.consoleLogger("[client]")
    val r1: Task[Double] = average(List(1.0, 2.0, 3.0)).runWithContext(at = serviceB, ctx, M)
    val r2: Task[Double] = average2(List(1.0, 2.0)).runWithContext(at = serviceB, ctx, M)
    val r3: Task[Double] = average3((0 to 10).map(_.toDouble).toList).runWithContext(at = serviceB, ctx, M)
    val r4: Task[Double] = average4((1 to 5).map(_.toDouble).toList).runWithContext(at = serviceB, ctx, M)
    val r5: Task[Double] = average5((1 to 5).map(_.toDouble).toList).runWithContext(at = serviceB, ctx, M)
    println { "RESULT 1: " + r1.run }
    println
    println { "RESULT 2: " + r2.run }
    println
    println { "RESULT 3: " + r3.run }
    println
    println { "RESULT 4: " + r4.run }
    println
    println { "RESULT 5: " + r5.run }
  }
  finally {
    stopA()
    stopB()
    transport.shutdown()
  }
}
