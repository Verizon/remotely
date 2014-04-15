package remotely
package examples

object CircuitBreaking {
  val stop = server.start("echo5-server")(Handler(_.take(5)), new InetSocketAddress("localhost", 8080), None)
  readLine()
  stop()
}

