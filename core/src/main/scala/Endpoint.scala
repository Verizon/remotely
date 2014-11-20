package remotely

import akka.actor.{Actor,ActorRef,ActorLogging,ActorSystem,OneForOneStrategy,Props,SupervisorStrategy}
import akka.io.{BackpressureBuffer,IO,Tcp,SslTlsSupport,TcpPipelineHandler}
import akka.util.ByteString
import java.net.{InetSocketAddress,Socket,URL}
import javax.net.ssl.SSLEngine
import scalaz.concurrent.Strategy
import scalaz.std.anyVal._
import scalaz.concurrent.Task
import scalaz.syntax.functor._
import scalaz.stream.{async,Channel,Exchange,io,Process,nio,Process1, process1, Sink}
import scalaz.stream.async.mutable.Queue
import scodec.bits.{BitVector}
import scala.concurrent.duration._
import scalaz._
import Process.{Await, Emit, Halt, emit, await, halt, eval, await1, iterate}
import scalaz.stream.merge._

/**
 * A 'logical' endpoint for some service, represented
 * by a possibly rotating stream of `Connection`s.
 */
trait Endpoint extends Handler {
  def apply(in: Process[Task,BitVector]): Process[Task,BitVector]

/*
  def get: Task[Connection] = connections.once.runLast.flatMap {
    case None => Task.fail(new Exception("No available connections"))
    case Some(a) => Task.now(a)
  }
 */
/*
  /**
   * Adds a circuit-breaker to this endpoint that "opens" (fails fast) after
   * `maxErrors` consecutive failures, and attempts a connection again
   * when `timeout` has passed.
   */
  def circuitBroken(timeout: Duration, maxErrors: Int): Task[Endpoint] =
    CircuitBreaker(timeout, maxErrors).map { cb =>
      Endpoint(connections.map(c => bs => c(bs).translate(cb.transform)))
    }
 */
}

object Endpoint {

  def empty: Endpoint = new Endpoint {
    def apply(in: Process[Task,BitVector]) = Process.fail(new Exception("no available connections"))
  }

  def single(transport: Handler): Endpoint = new Endpoint {
    def apply(in: Process[Task,BitVector]): Process[Task,BitVector] = transport(in)
  }
}


