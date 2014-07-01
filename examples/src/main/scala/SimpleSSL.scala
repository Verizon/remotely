package remotely
package examples

import java.net.InetSocketAddress
import scalaz.concurrent.Task

import codecs._

object SimpleSSL extends App {

  import Simple.{env,addr,sum}
  import Remote.implicits._

  println(env)

  val clientCtx = SSLSetup.context(
    "Paul-test-client",
    "ssl-testing/client_cert.pem",
    "ssl-testing/client_key.pem",
    "ssl-testing/CA.pem").run

  val serverCtx = SSLSetup.context(
    "Paul-test-server",
    "ssl-testing/server_cert.pem",
    "ssl-testing/server_key.pem",
    "ssl-testing/CA.pem").run

  val serverSslProvider = tls.enableCiphers(tls.ciphers.rsa: _*)(tls.fromContext(serverCtx))
  val clientSslProvider = tls.enableCiphers(tls.ciphers.rsa: _*)(tls.fromContext(clientCtx))

  // create a server for this environment
  val server = env.serveSSL(addr, tls.server(serverSslProvider))(Monitoring.consoleLogger("[server]"))

  // to actually run a remote expression, we need an endpoint
  implicit val clientPool = akka.actor.ActorSystem("rpc-client")

  try {
    val expr: Remote[Int] = sum(List(0,1,2,3,4))
    val loc: Endpoint = Endpoint.singleSSL(tls.client(clientSslProvider))(addr) // takes ActorSystem implicitly
    val result: Task[Int] = expr.runWithContext(loc, Response.Context.empty, Monitoring.consoleLogger("[client]"))

    // running a couple times just to see the latency improve for subsequent reqs
    println { result.run; result.run; result.run }
  }
  finally {
    // hack to allow asynchronous actor shutdown messages to propagate,
    // without this, we get some dead letter logging
    // I'm sure there's a better way to do this
    println("shutting down...")
    Thread.sleep(1000)
    server()
    clientPool.shutdown()
    println("..done shutting down")
  }
}
