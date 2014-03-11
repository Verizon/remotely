package srpc
package examples

import java.net.InetSocketAddress
import server.Handler

object EchoServer extends App {
  val stop = server.start("echo-server")(Handler.id, new InetSocketAddress("localhost", 8080))
  readLine()
  stop()
}
