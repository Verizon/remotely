package remotely
package transport
package akka

import org.apache.commons.pool2.ObjectPool
import scalaz.concurrent.Task
import scalaz.stream.{Process,Process1, Cause}
import Cause.End
import Process.Halt
import scodec.bits.BitVector
import _root_.akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, Props}
import _root_.akka.pattern.ask
import _root_.akka.io.SslTlsSupport
import _root_.akka.io.{Tcp,TcpPipelineHandler}
import _root_.akka.util.ByteString
import _root_.akka.util.Timeout
import java.net.InetSocketAddress
import javax.net.ssl.SSLEngine
import org.apache.commons.pool2.impl.DefaultPooledObject
import scala.util.Failure
import scala.util.Success
import scalaz.stream.{Process,async,process1}
import scalaz.stream.io.resource
import scalaz.concurrent.{Strategy,Task}
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.FiniteDuration
import org.apache.commons.pool2._
import scodec.bits.BitVector
import scalaz.{-\/,\/,\/-}
import scalaz.syntax.traverse._

class AkkaTransport(system: ActorSystem, val pool: ObjectPool[Future[ServerConnection]]) extends Handler {
  import system.dispatcher
  def apply(toServer: Process[Task, BitVector]): Process[Task, BitVector] = {
    val c = pool.borrowObject
    hookUpConnection(c, toServer).onHalt {
      case Cause.End =>
        pool.returnObject(c)
        Halt(Cause.End)
      case cause =>
        pool.invalidateObject(c)
        Halt(cause)
    }
  }

  def serverTask(server: Future[ServerConnection]): Task[ServerConnection] = {
    Task.async {(cb: (Throwable \/ ServerConnection) => Unit) =>
      server.onComplete {
        case Success(server) =>
          cb(\/-(server))
        case Failure(t) =>
          cb(-\/(t))
      }}
  }

  def hookUpConnection(server: Future[ServerConnection], toServer: Process[Task,BitVector]): Process[Task, BitVector] = {
    val fromServer = async.unboundedQueue[BitVector](Strategy.Sequential)
    val s: Task[ServerConnection] = for {
      s <- serverTask(server)
      _ = s.connection ! Associate(fromServer)
      _ <- toServer.evalMap { bytes => Task.delay { s.writeBytes(bytes) } }.run
    } yield s

    Process.await(s)(server => fromServer.dequeue)
  }
}

object AkkaTransport {
  def single(system: ActorSystem, host: InetSocketAddress): AkkaTransport =
    new AkkaTransport(system, AkkaConnectionPool.default(system,Process.constant(host)))

  def singleSSL(createEngine: () => SSLEngine)(system: ActorSystem, host: InetSocketAddress): AkkaTransport =
    new AkkaTransport(system, AkkaConnectionPool.defaultSSL(system,Process.constant(host), createEngine))
}
