package remotely
package example.benchmark
package server

import scalaz.concurrent._
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

class BenchmarkServerImpl extends BenchmarkServer with transformations {
  override def identityLarge = (large: LargeW) => Response[LargeW]((c: Response.Context) => Task.now{toLargeW(fromLargeW(large))})
  override def identityMedium = (med: MediumW) => Response[MediumW]((c: Response.Context) => Task.now{toMediumW(fromMediumW(med))})
  override def identityBig = (big: BigW) => Response[BigW]((c: Response.Context) => Task.now{toBigW(fromBigW(big))})

}

object Main {
  def main(argv: Array[String]): Unit = {
    val threadNo = new AtomicInteger(0)
    val port = Integer.parseInt(argv(0))
    val addr = new java.net.InetSocketAddress("localhost", port)
    val server = new BenchmarkServerImpl
    val threadPool = Executors.newFixedThreadPool(Integer.parseInt(argv(1)), new ThreadFactory {
                                                        override def newThread(r: Runnable): Thread = new Thread(r, "remotely - " + threadNo.incrementAndGet())
                                                      })
    val shutdown: () => Unit = server.environment.serveNetty(addr, threadPool,Monitoring.empty)
  }
}
