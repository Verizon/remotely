package remotely
package transport.akka

import akka.actor.{Actor,ActorLogging,ActorRef,ActorSystem,Props}
import akka.io.{BackpressureBuffer,IO,Tcp,SslTlsSupport,TcpPipelineHandler}
import akka.util.ByteString
import java.net.InetSocketAddress
import javax.net.ssl.SSLEngine
import scala.concurrent.duration.FiniteDuration
import scalaz.concurrent.Task
import scalaz.stream.{async,Process}

object HandlerServer {

  /**
    * Build an `Actor` for this handler. The actor responds to the following
    * messages: `akka.io.Tcp.Received` and `akka.io.Tcp.ConnectionClosed`.
    */
  def actor(system: ActorSystem, handler: Handler)(idleTimeout: FiniteDuration, conn: => ActorRef): ActorRef = {
    system.actorOf(Props(classOf[ServerActor], conn, idleTimeout, handler))
  }

  /**
   * Start a server at the given address, using the `Handler`
   * for processing each request. Returns a thunk that can be used
   * to terminate the server.
   */
  def start(name: String)(idleTimeout: FiniteDuration, h: Handler, addr: InetSocketAddress, ssl: Option[() => SSLEngine] = None): () => Unit = {
    val system = ActorSystem(name)
    val actor = system.actorOf(Props(new HandlerServer(idleTimeout,h, addr, ssl)))
                              () => { system.shutdown() }
  }
}

/**
 * Create a server on the given `InetSockeAddress`, using `handler` for processing
 * each request, and using `ssl` to optionally
 */
class HandlerServer(idleTimeout: FiniteDuration,handler: Handler, addr: InetSocketAddress, ssl: Option[() => SSLEngine] = None) extends Actor with ActorLogging {

  import context.system

  override def preStart = {
    log.debug("server attempting to bind to: " + addr)
    IO(Tcp) ! Tcp.Bind(self, addr, options = Tcp.SO.KeepAlive(on = true) :: Nil)
  }

  override def postStop = {
    log.info("server shut down")
  }

  def receive = {
    case b @ Tcp.Bound(localAddress) =>
      log.info("server bound to: " + localAddress)
    case Tcp.CommandFailed(_: Tcp.Bind) =>
      log.error("server failed to bind to: " + addr + ", shutting down")
      context stop self
    case Tcp.Connected(remote, _) =>
      log.debug("connection established")
      val connection = sender
      val pipeline = ssl.map { engine =>
        val sslEngine = engine()
        log.debug("server enabled cipher suites: " + sslEngine.getEnabledCipherSuites.toList)
        val init = TcpPipelineHandler.withLogger(log,
          new SslTlsSupport(sslEngine) >>
          new BackpressureBuffer(lowBytes = 128l, highBytes = 1024l * 16l, maxBytes = 4096l * 1000l * 100l))
        lazy val sslConnection: ActorRef =
          context.actorOf(TcpPipelineHandler.props(
            init,
            connection,
            HandlerServer.actor(context.system,handler)(idleTimeout, sslConnection))) // tie the knot, give the handler actor a reference to
                                                           // overall connection actor, which does SSL
        sslConnection
      } getOrElse { HandlerServer.actor(context.system, handler)(idleTimeout, connection) }
      connection ! Tcp.Register(pipeline/*, keepOpenOnPeerClosed = true*/)
  }
}

