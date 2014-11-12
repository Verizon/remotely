package remotely
package examples

import java.net.InetSocketAddress
import server.Handler
import scala.concurrent.duration.DurationInt

object Echo5Server extends App {
  val stop = server.start("echo5-server")(5.seconds, Handler(_.take(5)), new InetSocketAddress("localhost", 8080), None)
  readLine()
  stop()
}
