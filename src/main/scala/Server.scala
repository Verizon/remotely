package remotely

import java.net.InetSocketAddress
import scala.concurrent.duration._
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
   * environment and a values environment. This `Task` is
   * guaranteed not to fail - failures will be encoded as
   * responses, to be sent back to the client, and one can
   * use the `monitoring` argument if you wish to observe
   * these failures.
   */
  def handle(env: Environment)(request: BitVector)(monitoring: Monitoring): Task[BitVector] =
    Task.delay(System.nanoTime).flatMap { startNanos => Task.suspend {
      // decode the request from the environment
      val (trailing, (respEncoder,ctx,r)) =
        codecs.requestDecoder(env).decode(request)
              .fold(e => throw new Error(e), identity)
      val expected = Remote.refs(r)
      val unknown = (expected -- env.values.keySet).toList
      if (unknown.nonEmpty) // fail fast if the Environment doesn't know about some referenced values
        fail(s"[validation] server does not have referenced values:\n${unknown.mkString('\n'.toString)}")
      else if (trailing.nonEmpty) // also fail fast if the request has trailing bits (usually a codec error)
        fail(s"[validation] trailing bytes in request: ${trailing.toByteVector}")
      else // we are good to try executing the request
        eval(env.values)(r)(ctx).flatMap {
          a =>
            val deltaNanos = System.nanoTime - startNanos
            val delta = Duration.fromNanos(deltaNanos)
            val result = right(a)
            // monitoring.handled(ctx, r, expected, result, delta)
            monitoring.handled(r, expected, result, delta)
            toTask(codecs.responseEncoder(respEncoder).encode(result))
        }.attempt.flatMap {
          // this is a little convoluted - we catch this exception just so
          // we can log the failure using `monitoring`, then reraise it
          _.fold(
            e => {
              val deltaNanos = System.nanoTime - startNanos
              val delta = Duration.fromNanos(deltaNanos)
              monitoring.handled(r, expected, left(e), delta)
              Task.fail(e)
            },
            bits => Task.now(bits)
          )
        }
    }}.attempt.flatMap { _.fold(
      e => toTask(codecs.responseEncoder(codecs.utf8).encode(left(formatThrowable(e)))),
      bits => Task.now(bits)
    )}

  val P = Process

  /** Evaluate a remote expression, using the given (untyped) environment. */
  def eval[A](env: Values)(r: Remote[A]): Response[A] = {
    import Remote._
    r match {
      case Async(a, _, _) => Response.async(a)
      case Local(a,_,_) => Response.now(a)
      case Ref(name) => env.values.lift(name) match {
        case None => Response.delay { sys.error("Unknown name on server: " + name) }
        case Some(a) => a().asInstanceOf[Response[A]]
      }
      // on the server, only concern ourselves w/ tree of fully saturated calls
      case Ap1(Ref(f),a) => env.values(f)(eval(env)(a)) .asInstanceOf[Response[A]]
      case Ap2(Ref(f),a,b) => env.values(f)(eval(env)(a), eval(env)(b)) .asInstanceOf[Response[A]]
      case Ap3(Ref(f),a,b,c) => env.values(f)(eval(env)(a), eval(env)(b), eval(env)(c)) .asInstanceOf[Response[A]]
      case Ap4(Ref(f),a,b,c,d) => env.values(f)(eval(env)(a), eval(env)(b), eval(env)(c), eval(env)(d)) .asInstanceOf[Response[A]]
      case _ => Response.delay { sys.error("unable to interpret remote expression of form: " + r) }
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
