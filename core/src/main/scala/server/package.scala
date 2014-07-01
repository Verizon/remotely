package remotely

import akka.actor.{ActorSystem, Props}
import java.net.InetSocketAddress
import javax.net.ssl.SSLEngine

package object server {

  /**
   * Start a server at the given address, using the `Handler`
   * for processing each request. Returns a thunk that can be used
   * to terminate the server.
   */
  def start(name: String)(h: Handler, addr: InetSocketAddress, ssl: Option[() => SSLEngine] = None): () => Unit = {
    val system = ActorSystem(name)
    val actor = system.actorOf(Props(new HandlerServer(h, addr, ssl)))
    () => { system.shutdown() }
  }

}
