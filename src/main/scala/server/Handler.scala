package srpc.server

import scalaz.stream.{async,Process}
import scalaz.concurrent.Task
import scodec.bits.ByteVector
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
    private val (queue, src) = async.localQueue[ByteVector]
    @volatile var open = true
    lazy val connection = conn

    override def preStart = {
      apply(src).evalMap { b =>
        Task.delay {
          if (open) connection ! Tcp.Write(ByteString(b.toArray))
        }
      }.run.runAsync { e =>
        e.leftMap { err =>
          log.error("uncaught exception in connection-processing logic: " + err)
          log.error(err.getStackTrace.mkString("\n"))
        }
        if (open) {
          log.debug("done writing, closing connection")
          log.debug("server initiating connection close: " + connection)
          connection ! Tcp.Close
        }
      }
    }

    def receive = {
      case Tcp.Received(data) =>
        val bytes = ByteVector(data.toArray)
        if (log.isDebugEnabled) log.debug("server got bytes: " + bytes.toBitVector)
        queue.enqueue(bytes)
      case Tcp.Aborted => open = false; queue.fail(new Exception("connection aborted"))
      case Tcp.ErrorClosed(msg) => open = false; queue.fail(new Exception("I/O error: " + msg))
      case _ : Tcp.ConnectionClosed => open = false; queue.close
    }
  }))
}

object Handler {

  /** Create a handler from a function from input to output stream. */
  def apply(f: Process[Task,ByteVector] => Process[Task,ByteVector]): Handler =
    new Handler {
      def apply(source: Process[Task,ByteVector]) = f(source)
    }

  /** Create a handler from an effectful function that receives the full input. */
  def strict(f: ByteVector => Task[ByteVector]): Handler =
    Handler(_.chunkAll.map(_.foldLeft(ByteVector.empty)(_ ++ _)).evalMap(f))

  /** The identity handler, which echoes its input stream. */
  def id: Handler = Handler(a => a)
}
