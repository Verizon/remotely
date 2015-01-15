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
package client

import example.benchmark.{SmallW, BigW, MediumW, LargeW}
import scalaz.concurrent.Task
import scalaz.Monoid
import scalaz.syntax.validation._
import scalaz.syntax.monoid._
import scalaz.std.anyVal._
import scalaz.std.map._
import remotely._
import remotely.Remote._
import remotely.Remote.implicits._
import remotely.{Monitoring,Response,Endpoint,codecs}, codecs._, Response.Context
import remotely.transport.netty._
import scodec.Codec
import remotely.example.benchmark.protocol._

case class Result(success: Int,
                   failure: Int,
                   successTime: Long,
                   successTimeMin: Long,
                   successTimeMax: Long,
                   failureTime: Long,
                  failures: Map[String,Int]) {

  def meanResponse = if(success == 0) 0 else successTime / success

  override def toString =
    s"OK: $success, KO: $failure, minResponse: $successTimeMin, meanResponse: ${meanResponse}  maxResponse: $successTimeMax\nErrors: " + failures.map{ case (k,v) => s"    $k -> $v" }
}


object Result {
  implicit val resultMonoid: Monoid[Result] = new Monoid[Result] {
    def zero: Result = Result(0,0,0,0,0,0,Map.empty)
    def append(x: Result, y: => Result) =
      Result(x.success |+| y.success,
             x.failure |+| y.failure,
             x.successTime |+| y.successTime,
             x.successTimeMin min y.successTimeMin,
             x.successTimeMax max y.successTimeMax,
             x.failureTime |+| y.failureTime,
             x.failures |+| y.failures)

  }
}

class Test(results: Results, task: Task[_]) extends Runnable { 

  var dead = false
  def die(): Unit = {
    dead = true
  }

  def error(start: Long)(e: Throwable): Unit = {
    results.failure(e.getMessage, System.currentTimeMillis - start)
  }

  def success(start: Long)(x: Any): Unit = {
    results.success(System.currentTimeMillis - start)
  }

  def run(): Unit = {
    while(!dead) {
      val start = System.currentTimeMillis
      task.runAsync(_.fold(error(start), success(start)))
    }
  }
}


class Results {
  var results = Monoid[Result].zero

  def success(t: Long): Unit = {
    synchronized {
      results = Result(results.success + 1,
                       results.failure,
                       results.successTime + t,
                       results.successTimeMin min t,
                       results.successTimeMax max t,
                       results.failureTime,
                       results.failures)
    }
  }

  def updateFailures(old: Map[String,Int], f: String): Map[String,Int] = old.get(f).fold(old + (f -> 1))(n => old + (f -> (n+1)))

  def failure(reason: String, t: Long): Unit = {
    synchronized {
      results = Result(results.success,
                       results.failure + 1,
                       results.successTime,
                       results.successTimeMin,
                       results.successTimeMax,
                       results.failureTime + t,
                       updateFailures(results.failures, reason))
    }
  }


  def print(): Unit = {
    synchronized {
      println(results.toString)
    }
  }
}

object BenchmarkClientMain extends TestData with transformations {

  def usage() {
    println("Usage: BenchmarkClientMain port threads seconds")
    Runtime.getRuntime.exit(1)
  }

  /**
    * run a client against the Benchmark server
    * 
    * Usage:
    *  main port numThreads duration
    * 
    * port is the port number the server is running on
    * numThreads is the number of client threads to start
    * duration is the number of seconds to run the benchmark
    */
  def main(argv: Array[String]): Unit = {
    if(argv.length < 3) usage()

    val port = Integer.parseInt(argv(0))
    val addr = new java.net.InetSocketAddress("localhost", port)
    val nettyTrans = NettyTransport.single(addr, server.BenchmarkClient.expectedSignatures, monitoring = Monitoring.consoleLogger("benchmarkClient"))
    val endpoint = Endpoint.single(nettyTrans)
    val num = Integer.parseInt(argv(1))
    val duration = java.lang.Long.parseLong(argv(2))
    val results = new Results
    val end = System.currentTimeMillis + (duration * 1000)

    val testers = (1 to num).toList.map{ _ =>
      new Test(results, server.BenchmarkClient.identityBig(toBigW(bigIn)).runWithoutContext(endpoint))
//      new Test(results, BenchmarkClient.identityMedium(toMediumW(medIn)).runWithoutContext(endpoint))
//      new Test(results, BenchmarkClient.identityLarge(toLargeW(largeIn)).runWithoutContext(endpoint))
    }
    val threads = testers.map(new Thread(_))

    threads.foreach(_.start)

    while( System.currentTimeMillis < end) {
      Thread.sleep(5000)
      results.print()
    }

    testers.foreach(_.die())
    threads.foreach(_.join)

    results.print()
    nettyTrans.shutdown()
  }
}

