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

import scalaz.concurrent._
import scalaz.stream.Process
import java.util.concurrent.atomic.AtomicInteger

class BenchmarkServerImpl extends BenchmarkServer with transformations {
  override def identityLarge = (large: LargeW) => Response.now(toLargeW(fromLargeW(large)))
  override def identityMedium = (med: MediumW) => Response.now(toMediumW(fromMediumW(med)))
  override def identityBig = (big: BigW) => Response.now(toBigW(fromBigW(big)))
  override def streamLarge = (largeStream: Process[Task, LargeW]) => Response.now(largeStream.map(large => toLargeW(fromLargeW(large))))
  override def streamMedium = (mediumStream: Process[Task, MediumW]) => Response.now(mediumStream.map(medium => toMediumW(fromMediumW(medium))))
  override def streamBig = (bigStream: Process[Task, BigW]) => Response.now(bigStream.map(big => toBigW(fromBigW(big))))
}

object Main {
  def usage() {
    println("usage: BenchmarkServerImpl port numThreads")
    Runtime.getRuntime.exit(1)
  }

  def main(argv: Array[String]): Unit = {
    if(argv.length < 2) usage()

    val threadNo = new AtomicInteger(0)
    val port = Integer.parseInt(argv(0))
    val addr = new java.net.InetSocketAddress("localhost", port)
    val server = new BenchmarkServerImpl
    val shutdown: Task[Unit] = server.environment.serve(addr).run
  }
}
