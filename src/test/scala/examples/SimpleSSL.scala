package remotely
package examples

import java.net.InetSocketAddress
import scalaz.concurrent.Task

import codecs._

object SimpleSSL extends App {

  import Simple.{env,addr,sum}
  import Remote.implicits._

  println(env)

  // create a server for this environment
  val server = env.serve(addr, Monitoring.consoleLogger("[server]"))

  // to actually run a remote expression, we need an endpoint
  implicit val clientPool = akka.actor.ActorSystem("rpc-client")

  val expr: Remote[Int] = sum(List(0,1,2,3,4))
  val loc: Endpoint = Endpoint.singleSSL(tls.default)(addr) // takes ActorSystem implicitly
  val result: Task[Int] = expr.run(loc, Monitoring.consoleLogger("[client]"))

  // running a couple times just to see the latency improve for subsequent reqs
  try println { result.run; result.run; result.run }
  finally {
    // hack to allow asynchronous actor shutdown messages to propagate,
    // without this, we get some dead letter logging
    // I'm sure there's a better way to do this
    Thread.sleep(1000)
    server()
    clientPool.shutdown()
  }
}
