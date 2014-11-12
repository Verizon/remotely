package remotely
package transport.akka

import akka.actor.{Actor,ActorLogging,ActorRef}
import akka.io.Tcp
import scalaz.stream.async
import scodec.bits.BitVector

trait EndpointActor {
  self: Actor with ActorLogging =>

  import context._

  val connection: ActorRef
  var valid: Boolean = true
  var remaining: Long = 0
  var fromRemote: async.mutable.Queue[BitVector] = null

  protected def fail(error: String): Unit = {
    log.error(s"connection failure. cause: $error")
    valid = false
    if(fromRemote != null) {
      fromRemote.fail(new Exception(error)).run
      fromRemote = null
    }

    connection ! Tcp.Close
  }

  protected def close(): Unit = {
    fromRemote.close.run
    fromRemote = null;
  }

  def logBecome(next: String, nexts: Receive): Unit = {
    log.debug(s"becomming: $next")
    become(nexts)
  }

  /**
    * A receive function which is a fallback in all states
    * It watches for dicsonnection events
    */
  def default: Receive = {
    case Disconnect => fail("disconnecting")
    case IsValid => sender ! valid
    case Tcp.PeerClosed => fail("remote disconnected")
    case Tcp.Aborted => fail("connection aborted")
    case Tcp.ErrorClosed(msg) => fail("I/O error: " + msg)
    case _ : Tcp.ConnectionClosed =>
      if(remaining > 0) fail("didn't receive a complete stream")
      else close()
  }

  /**
    * A request has been sent to the server, we are awaiting the
    * initial packet of results
    */
  def awaitingFrame0: Receive = {
    case Tcp.Received(data) =>
      frameBits(BitVector(data.toArray))
  } 

  val awaitingFrame = awaitingFrame0 orElse default

  protected def frameBits(bits: BitVector): Unit = {
    codecs.int32.decode(bits).fold(fail, frameHead)
  }

  protected def writeRemaining(bits: BitVector) = {
    if(bits.size > remaining) {
      val (last,next) = bits.splitAt(remaining)
      fromRemote.enqueueOne(last).run
      frameBits(next)
    } else {
      fromRemote.enqueueOne(bits).run
      if(bits.size == remaining) {
        remaining = 0
        close()
      } else {
        remaining -= bits.size
        logBecome("receiving",receiving)
      }
    }
  }
  /**
    * We've received some but not all of the reply
    */
  def receiving0: Receive = {
    case Tcp.Received(data) =>
      writeRemaining(BitVector(data.toArray))
  } 

  val receiving = receiving0 orElse default

  /**
    * we've received the inital bits, which starts with a header with
    * the number of bits in the frame. followed by some or all of the
    * bytes in the frame.
    * 
    * A zero length frame signifies the end of the input, this
    * connection can be released
    * 
    * If the packet is self contained, and all the bits expected, we
    * go back to the awaitingFrame state
    * 
    * otherwise figure out how many bits are left and switch to the
    * receiving state
    */
  protected def frameHead(decoded: (BitVector,Int)): Unit = {
    if(decoded._2 == 0) {
      close()
    } else {
      remaining = decoded._2
      writeRemaining(decoded._1)
    }
  }
}
