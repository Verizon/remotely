package remotely
package examples

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import scala.concurrent.duration.DurationInt

object Echo5Server extends App {
  val stop = transport.netty.NettyServer.start(new InetSocketAddress("localhost", 8080), Handler(_.take(5)), Executors.newCachedThreadPool)
  readLine()
  stop()
}
