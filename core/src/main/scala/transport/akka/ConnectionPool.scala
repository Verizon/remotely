package remotely
package transport.akka

import _root_.akka.actor.{Actor,ActorLogging,ActorRef,ActorSystem,Props}
import _root_.akka.util.{ByteString,Timeout}
import _root_.akka.io.{IO,Tcp,TcpPipelineHandler,SslTlsSupport}
import _root_.akka.pattern.ask
import javax.net.ssl.SSLEngine
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.ObjectPool
import org.apache.commons.pool2.PoolUtils
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import scala.concurrent.Await
import scodec.bits.BitVector
import scala.concurrent.Future
import scalaz.stream.{Process,async}
import scalaz.concurrent.Task
import scalaz.{-\/,\/,\/-}
import java.net.InetSocketAddress
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

case class ServerConnection(writeBytes: BitVector => Unit, connection: ActorRef)

object AkkaConnectionPool {
  def default(system: ActorSystem, hosts: Process[Task,InetSocketAddress]): ObjectPool[Future[ServerConnection]] = 
    PoolUtils.erodingPool(new GenericObjectPool[Future[ServerConnection]](new AkkaConnectionPool(system, hosts, None, 5 seconds)))


  def defaultSSL(system: ActorSystem, hosts: Process[Task,InetSocketAddress], createEngine: () => SSLEngine): ObjectPool[Future[ServerConnection]] =
    PoolUtils.erodingPool(new GenericObjectPool[Future[ServerConnection]](new AkkaConnectionPool(system, hosts, Some(createEngine), 5 seconds)))

}

class AkkaConnectionPool(system: ActorSystem,
                       hosts: Process[Task,InetSocketAddress],
                       createEngine: Option[() => SSLEngine],
                       timeout: FiniteDuration)
    extends BasePooledObjectFactory[Future[ServerConnection]] {
  import system.dispatcher
  implicit val to = Timeout(timeout)

  override def create: Future[ServerConnection] = {
    val connector = system.actorOf(Props(classOf[Connector],hosts.once.runLast.run.get, createEngine))

    for {
      maybeC <- (connector ? Connector.Ohai).mapTo[Connector.Result]
      c <- maybeC.fold(Future.failed, Future.successful)
    } yield ServerConnection(c._1, c._2)

  }

  override def destroyObject(p: PooledObject[Future[ServerConnection]]) =
    p.getObject foreach (_.connection ! Disconnect)

  override def validateObject(fc: PooledObject[Future[ServerConnection]]): Boolean = {
    Await.result(for {
                   c <- fc.getObject
                   v <- (c.connection ? IsValid).mapTo[Boolean]
                 } yield v, timeout)
  }

  override def wrap(c: Future[ServerConnection]): PooledObject[Future[ServerConnection]] = new DefaultPooledObject(c)

  override def passivateObject(c: PooledObject[Future[ServerConnection]]): Unit = ()

  // TODO: alert on destroyed objects
}

case class Associate(fromServer: async.mutable.Queue[BitVector])

/**
  * Ask if this connection is valid // not currently used, STU
  */
case object IsValid
case object Disconnect

/**
  * An actor which handles the server side of a TCP connection,
  * 
  * States:
  *          +---------------------------------------------------------+    +---------------------------+
  *          |                        size == 0                        |    |                           |
  *          v                                                         |    v                           |
  *  +--------------+  Associate  +---------------+  Tcp.Receive   +------------+  > 0    ------------+ |
  *  | Disconnected | ----------> | awaitingFrame | -------------> | remaining? | ------> | receiving |-+
  *  +--------------+             +---------------+                +------------+         +-----------+
  *                                       ^                               |
  *                                       |             == 0              |
  *                                       +-------------------------------+
  **/
class ClientConnection(val connection: ActorRef) extends Actor with EndpointActor with ActorLogging {
  import context._

  override protected def close(): Unit = {
    super.close()
    logBecome("disconnected",disconnected)
  }

  def disconnected0: Receive = {
    case Associate(fromServer) =>
      this.fromRemote = fromServer
      logBecome("awaitingFrame",awaitingFrame)
  }

  val disconnected = disconnected0 orElse default

  def receive = disconnected // initialState

}

object Connector {
  type Result = Throwable \/ (BitVector => Unit, ActorRef)
  case object Ohai
}

/**
  *  Actor which is created in order to ask akka i/o for a new
  *  connection if you tell me who you are with "Ohai" I pinky promise
  *  to respond with a Connector.Result
  */
class Connector(host: InetSocketAddress, createEngine: Option[() => SSLEngine]) extends Actor with ActorLogging {
  import context.system
  import Connector._

  override def preStart() =
    IO(Tcp)(context.system) ! Tcp.Connect(host)

  def createSSLConnection(connection: ActorRef, core: ActorRef)(engine: () => SSLEngine): (BitVector => Unit, ActorRef) = {
    val sslEngine = engine()
    log.debug("client enabled cipher suites: " + sslEngine.getEnabledCipherSuites.toList)
    val init = TcpPipelineHandler.withLogger(log, new SslTlsSupport(sslEngine))
    val pipeline = context.actorOf(TcpPipelineHandler.props(init, connection, core))

//STU    Akka.onComplete(system, pipeline) {
//STU      // Did we complete normally? If not, raise an exception
//STU      if (!normal)
//STU        src.fail(new Exception("SSL pipeline terminated, most likely because of an error in negotiating SSL session")).run
//STU    }

    val writeBytes = (bs: BitVector) => pipeline ! init.Command(Tcp.Write(ByteString(bs.bytes.toArray)))
    // Underlying connection needs to `keepOpenOnPeerClosed` if using SSL
    // NB: the client does not close the connection; the server closes the
    // connection when it is finished writing (or in the event of an error)
    connection ! Tcp.Register(pipeline, keepOpenOnPeerClosed = true)
    (writeBytes, pipeline)
  }

  def createNonSSLConnection(connection: ActorRef, core: ActorRef): (BitVector => Unit, ActorRef) = { 
    connection ! Tcp.Register(core)
    (((bs: BitVector) => connection ! Tcp.Write(ByteString(bs.bytes.toArray))), core)
  }

  def createConnection(connection: ActorRef, core: ActorRef): (BitVector => Unit, ActorRef) =
    createEngine.fold(createNonSSLConnection(connection,core))(createSSLConnection(connection,core))

  var creator: ActorRef = null
  var result: Result = null

  def maybeFulfill(): Unit = {
    if(creator != null && result != null) {
      creator ! result
      
//      context stop self // drop the mic
    }
  }

  def receive = {
    case Ohai =>
      creator = sender
      maybeFulfill

    case Tcp.CommandFailed(_: Tcp.Connect) ⇒
      log.error("connection failed to " + host)
      result = -\/(new Exception("connection failed to " + host))
      maybeFulfill

    case c @ Tcp.Connected(remoteAddr, localAddr) =>
      val core = context.actorOf(Props(classOf[ClientConnection], sender))
      result = \/-(createConnection(sender, core))
      maybeFulfill

  }
}
