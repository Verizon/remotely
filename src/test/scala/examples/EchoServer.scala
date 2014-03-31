package remotely
package examples

import java.net.InetSocketAddress
import server.Handler

object Echo5Server extends App {
  val stop = server.start("echo5-server")(Handler(_.take(5)), new InetSocketAddress("localhost", 8080))
  readLine()
  stop()
}
