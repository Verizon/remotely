package remotely

import java.net.InetSocketAddress
import scalaz.{\/,Monad}
import scalaz.\/.{right,left}
import scalaz.stream.{Bytes,merge,nio,Process}
import scalaz.concurrent.Task
import scodec.bits.{BitVector,ByteVector}
import scodec.Encoder
import remotely.server.Handler

object Server {

  /**
   * Handle a single RPC request, given a decoding
   * environment and a values environment.
   */
  def handle(env: Environment)(request: BitVector): Task[BitVector] = Task.suspend {
    val (trailing, (respEncoder,r)) =
      codecs.requestDecoder(env).decode(request)
            .fold(e => throw new Error(e), identity)
    val expected = Remote.refs(r)
    val unknown = (expected -- env.values.keySet).toList
    if (unknown.nonEmpty) fail(s"[validation] server does not have referenced values:\n${unknown.mkString('\n'.toString)}")
    else if (trailing.nonEmpty) fail(s"[validation] trailing bytes in request: ${trailing.toByteVector}")
    else eval(env.values)(r).flatMap {
      a => toTask(codecs.responseEncoder(respEncoder).encode(right(a)))
    }
  }.attempt.flatMap {
    _.fold(e => toTask(codecs.responseEncoder(codecs.utf8).encode(left(formatThrowable(e)))),
           bits => Task.now(bits))
  }

  val P = Process

  /**
   * Start an RPC server on the given port.
   */
  def start(env: Environment)(addr: InetSocketAddress): () => Unit =
    server.start("rpc-server")(
      Handler { bytes =>
        // we assume the input is a framed stream, and encode the response(s)
        // as a framed stream as well
        bytes.pipe(Handler.frames).evalMap(bs => handle(env)(bs.toBitVector).map(_.toByteVector)).pipe(Handler.frame)
      },
      addr
    )

  /** Evaluate a remote expression, using the given (untyped) environment. */
  def eval[A](env: Values)(r: Remote[A]): Task[A] = {
    import Remote._
    val T = Monad[Task]
    r match {
      case Async(a, _, _) => a
      case Local(a,_,_) => Task.now(a)
      case Ref(name) => env.values.lift(name) match {
        case None => Task.delay { sys.error("Unknown name on server: " + name) }
        case Some(a) => Task.now(a.asInstanceOf[A])
      }
      case Ap1(f,a) => T.apply2(eval(env)(f), eval(env)(a))(_(_))
      case Ap2(f,a,b) => T.apply3(eval(env)(f), eval(env)(a), eval(env)(b))(_(_,_))
      case Ap3(f,a,b,c) => T.apply4(eval(env)(f), eval(env)(a), eval(env)(b), eval(env)(c))(_(_,_,_))
      case Ap4(f,a,b,c,d) => T.apply5(eval(env)(f), eval(env)(a), eval(env)(b), eval(env)(c), eval(env)(d))(_(_,_,_,_))
    }
  }

  private def toTask[A](e: String \/ A): Task[A] =
    e.fold(e => Task.fail(new Error(e)),
           a => Task.now(a))

  def fail(msg: String): Task[Nothing] = Task.fail(new Error(msg))

  class Error(msg: String) extends Exception(msg)

  def formatThrowable(err: Throwable): String =
    err.toString + "\n stack trace:\n" + err.getStackTrace.mkString("\n")
}
