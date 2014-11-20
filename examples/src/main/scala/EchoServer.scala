package remotely
package examples

import java.net.InetSocketAddress
import scala.concurrent.duration.DurationInt

object Echo5Server extends App {
  val stop = transport.akka.HandlerServer.start("echo5-server")(5.seconds, Handler(_.take(5)), new InetSocketAddress("localhost", 8080), None)
  readLine()
  stop()
}
