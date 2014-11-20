package remotely
package transport.akka

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

class ServerActor(val connection: ActorRef, val idleTimeout: FiniteDuration, handler: Handler) extends Actor with EndpointActor with ActorLogging {
  import context._

  var stream: Option[Process[Task,BitVector]] = None

  def newQueue(): Unit = {
    fromRemote = async.unboundedQueue[BitVector](Strategy.Sequential)
    val stream1 = fromRemote.dequeue
    stream = Option(stream1)
    var read = 0L
    val stream2 = stream1.map { bv =>
      read += bv.size
      bv
    }

    var written = 0L
    val write: Task[Unit] = handler(stream2).evalMap { b =>
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
    stream = None
    log.error(s"FAILING because $error")
    super.fail(error)
    context stop self
  }

  override protected def close() = {
    stream = None
    logBecome("awaitingFrame", awaitingFrame)
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
