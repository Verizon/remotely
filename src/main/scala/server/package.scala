package srpc

import akka.actor.{ActorSystem, PoisonPill, Props}
import java.net.InetSocketAddress

package object server {

  /**
   * Start a server at the given address, using the `Handler`
   * for processing each request. Returns a thunk that can be used
   * to terminate the server.
   */
  def start(name: String)(h: Handler, addr: InetSocketAddress): () => Unit = {
    val system = ActorSystem(name)
    val actor = system.actorOf(Props(new HandlerServer(h, addr)))
    () => actor ! PoisonPill.getInstance
  }

}
