package remotely.server

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
 * Represents the logic of a connection handler, a function
 * from a stream of bytes to a stream of bytes, which will
 * be sent back to the client. The connection will be closed
 * when the returned stream terminates.
 *
 * NB: This type cannot represent certain kinds of 'coroutine'
 * client/server interactions, where the server awaits a response
 * to a particular packet sent before continuing.
 */
trait Handler {
  def apply(source: Process[Task,ByteVector]): Process[Task,ByteVector]

  /**
   * Build an `Actor` for this handler. The actor responds to the following
   * messages: `akka.io.Tcp.Received` and `akka.io.Tcp.ConnectionClosed`.
   */
  def actor(system: ActorSystem)(conn: => ActorRef): ActorRef = system.actorOf(Props(new Actor with ActorLogging {
    private val src = async.unboundedQueue[ByteVector](Strategy.Sequential)
    val queue = src.dequeue
    @volatile var open = true
    lazy val connection = conn

    override def preStart = {
      val write: Task[Unit] = apply(queue).evalMap { b =>
        Task.delay {
          if (open) {
            println(s"writing ${b.toArray.length} bytes")
            connection ! Tcp.Write(ByteString(b.toArray))
          } else throw Cause.End.asThrowable
        }
      }.run
      write.runAsync { e =>
        println("inside runAsync")
        e.leftMap { err =>
          log.error("uncaught exception in connection-processing logic: " + err)
          log.error(err.getStackTrace.mkString("\n"))
          connection ! Tcp.Close
        }
        if (open) {
          log.debug("done writing, closing connection")
          log.debug("server initiating connection close: " + connection)
          connection ! Tcp.Close
        }
        else {
          log.info("client initiated connection close")
          context stop self
        }
      }
    }

    def receive = {
      case Tcp.Received(data) =>
        val bytes = ByteVector(data.toArray)
        if (log.isDebugEnabled) log.debug("server got bytes: " + bytes.toBitVector)
        src.enqueueOne(bytes).run
      case Tcp.Aborted => open = false; src.fail(new Exception("connection aborted")).run
      case Tcp.ErrorClosed(msg) => open = false; src.fail(new Exception("I/O error: " + msg)).run
      case Tcp.PeerClosed => open = false; src.close.run
      case Tcp.Closed =>
        log.debug("connection closed gracefully by server, shutting down")
        context stop self
    }
  }))
}

object Handler {

  /** Create a handler from a function from input to output stream. */
  def apply(f: Process[Task,ByteVector] => Process[Task,ByteVector]): Handler =
    new Handler {
      def apply(source: Process[Task,ByteVector]) = f(source)
    }

  private[remotely] def frame: Process1[ByteVector,ByteVector] = {
    val core = Process.await1[ByteVector].map { bs =>
      codecs.int32.encodeValid(bs.size).toByteVector ++ bs
    }
    (core.repeat: Process1[ByteVector,ByteVector]) ++ // type inference fail
    Process.emit(codecs.int32.encodeValid(0).toByteVector)
  }

  /**
   * Break an input byte stream chunked at any granularity along frame
   * boundaries. Input consists of a stream of frames, where each frame
   * is just a number of bytes, encoded as an int32, followed by a
   * packet of that many bytes. End of stream is indicated with a frame
   * header whose byte count is <= 0. Output stream is the stream of frame
   * payloads.
   */
  private[remotely] def frames: Process1[ByteVector,ByteVector] = {
    def frameHeader(acc: ByteVector): Process1[ByteVector,ByteVector] =
      if (acc.size < 4) Process.await1[ByteVector].flatMap(bs => frameHeader(acc ++ bs))
      else codecs.int32.decode(acc.toBitVector).fold (
        errMsg => Process.fail(new IllegalArgumentException(errMsg)),
        { case (rem,size) => if (size <= 0) Process.halt else readFrame(size,rem) }
      )
    def readFrame(bytesToRead: Int, bits: BitVector): Process1[ByteVector,ByteVector] =
      if (bits.size / 8 >= bytesToRead) {
        val bytes = bits.toByteVector
        val frame = bytes.take(bytesToRead)
        Process.emit(frame) fby frameHeader(bytes.drop(bytesToRead))
      }
      else
        Process.await1[ByteVector].flatMap {
          bs => readFrame(bytesToRead, bits ++ BitVector(bs))
        }
    frameHeader(ByteVector.empty)
  }
}
