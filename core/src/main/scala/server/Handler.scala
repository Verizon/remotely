package remotely.server

import scala.concurrent.duration.FiniteDuration
import scalaz.concurrent.Strategy
import scalaz.stream.Cause
import scalaz.stream.{async,Process,Process1}
import scalaz.concurrent.Task
import scodec.bits.{BitVector,ByteVector}
import scodec.codecs
import akka.util.ByteString
import akka.actor.{Actor,ActorRef,ActorLogging,ActorSystem,Props}
import akka.io.Tcp
import remotely.transport.akka.EndpointActor

/**
 * Represents the logic of a connection handler, a function
 * from a stream of bytes to a stream of bytes, which will
 * be sent back to the client. The connection will be closed
 * when the returned stream terminates.
 *
 * NB: This type cannot represent certain kinds of 'coroutine'
 * client/server interactions, where the server awaits a response
 * to a particular packet sent before continuing.
 */
trait Handler extends (Process[Task,BitVector] => Process[Task,BitVector]) {
  def apply(source: Process[Task,BitVector]): Process[Task,BitVector]

  /**
   * Build an `Actor` for this handler. The actor responds to the following
   * messages: `akka.io.Tcp.Received` and `akka.io.Tcp.ConnectionClosed`.
   */
  def actor(system: ActorSystem)(idleTimeout: FiniteDuration, conn: => ActorRef): ActorRef = {
    system.actorOf(Props(classOf[ServerActor], conn, idleTimeout, this))
  }
}

/**
  * An actor which handles the server side of a TCP connection,
  * 
  * States:
  *          +-----------------------------+  +----------------------------+
  *          |           size == 0         |  |                            |
  *          v                             |  v                            |
  *  +---------------+  Tcp.Receive   +------------+  > 0    ------------+ |
  *  | awaitingFrame | -------------> | remaining? | ------> | receiving |-+
  *  +---------------+                +------------+         +-----------+
  *          ^                               |
  *          |             == 0              |
  *          +-------------------------------+
  **/

class ServerActor(val connection: ActorRef, val idleTimeout: FiniteDuration, handler: Process[Task,BitVector] => Process[Task,BitVector]) extends Actor with EndpointActor with ActorLogging {
  import context._

  var queue: Process[Task,BitVector] = null

  def newQueue(): Unit = {
    fromRemote = async.unboundedQueue[BitVector](Strategy.Sequential)
    queue = fromRemote.dequeue
    var read = 0L
    val queue2 = queue.map { bv =>
      read += bv.size
      bv
    }

    var written = 0L
    val write: Task[Unit] = handler(queue2).evalMap { b =>
      Task.delay {
        if (valid) {
          written += b.size
          connection ! Tcp.Write(ByteString(b.bytes.toArray))
        } else {
          log.error("attempt to write from an invalid connection")
          throw Cause.End.asThrowable
        }
      }
    }.run
    write.runAsync { e =>
      e.leftMap { err =>
        log.error("uncaught exception in connection-processing logic: " + err)
        log.error(err.getStackTrace.mkString("\n"))
        connection ! Tcp.Close
      }
      if (valid) {
        log.debug(s"read $read bits, then wrote $written bits")
      }
      else {
        connection ! Tcp.Close
        log.debug("client initiated connection close")
        context stop self
      }
    }
  }

  override protected def fail(error: String) = {
    queue = null
    log.error(s"FAILING because $error")
    super.fail(error)
    context stop self
  }

  override protected def close() = {
    queue = null
    super.close()
  }

  override protected def frameBits(bits: BitVector): Unit = {
    if(fromRemote == null) newQueue()
    super.frameBits(bits)
  }

  private def inherit(bits: BitVector): Unit = {
    fromRemote.close.run
    if(bits.size > 0) {
      frameBits(bits)
    } else close()
  }

  /* overridden here for inherit, which doesn't happen on a client */
  override protected def frameHead(decoded: (BitVector,Int)): Unit = {
    if(decoded._2 == 0) {
      inherit(decoded._1)
    } else {
      remaining = decoded._2
      writeRemaining(decoded._1)
    }
  }

  def receive = awaitingFrame
}

object Handler {

  /** Create a handler from a function from input to output stream. */
  def apply(f: Process[Task,BitVector] => Process[Task,BitVector]): Handler =
    new Handler {
      def apply(source: Process[Task,BitVector]) = f(source)
    }
}
