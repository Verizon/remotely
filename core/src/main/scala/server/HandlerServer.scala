package remotely.server

import akka.actor.{Actor,ActorLogging,ActorRef,ActorSystem,Props}
import akka.io.{BackpressureBuffer,IO,Tcp,SslTlsSupport,TcpPipelineHandler}
import akka.util.ByteString
import java.net.InetSocketAddress
import javax.net.ssl.SSLEngine
import scala.concurrent.duration.FiniteDuration
import scalaz.concurrent.Task
import scalaz.stream.{async,Process}

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
            handler.actor(context.system)(idleTimeout, sslConnection))) // tie the knot, give the handler actor a reference to
                                                           // overall connection actor, which does SSL
        sslConnection
      } getOrElse { handler.actor(context.system)(idleTimeout, connection) }
      connection ! Tcp.Register(pipeline/*, keepOpenOnPeerClosed = true*/)
  }
}

