package srpc

import java.net.InetSocketAddress
import scalaz.{\/,Monad}
import scalaz.stream.{Bytes,merge,nio,Process}
import scalaz.concurrent.Task
import scodec.bits.BitVector
import scodec.Encoder

object Server {

  /**
   * Handle a single RPC request, given a decoding
   * environment and a values environment.
   */
  def handle(env: Environment)(request: BitVector): Task[(Encoder[Any], Any)] = Task.suspend {
    Codecs.requestDecoder(env).flatMap { case (respEncoder,r) =>
      val expected = Remote.refs(r)
      val unknown = (expected -- env.values.keySet).toList
      if (unknown.isEmpty) Codecs.succeed((respEncoder -> r))
      else Codecs.fail(s"[validation] server does not have referenced values: $unknown")
    }.decode(request).fold(
      e => Task.fail(new Error(e)),
      { case (trailing, (respEncoder,r)) =>
         if (trailing.isEmpty) eval(env.values)(r).flatMap { a =>
           Task.now(respEncoder -> a)
         }
         else Task.fail(new Error("[validation] trailing bytes in request: "+trailing))
      }
    )
  }

  /**
   * Start a server on the given port, returning the stream
   * of log messages, which can also be used to halt the
   * server. This function has no side effects. You must
   * run the stream in order to process any requests.
   */
  def start(env: Environment)(addr: InetSocketAddress): Process[Task,String] =
    merge.mergeN(500) { nio.server(addr).map { _.once.evalMap {
      exch => fullyRead(exch.read)
              .flatMap (handle(env))
              .flatMap { case (enc,r) => Codecs.liftEncode(enc.encode(r)) }
              .attempt
              .flatMap (_.fold(
                e => Task.now(e.toString),
                resp => (Process(Bytes.of(resp.toByteArray)).toSource to exch.write)
                        .run.attempt.map(_.fold(_.toString, _ => "."))
              ))
    }}}

  /** Evaluate a remote expression, using the given (untyped) environment. */
  def eval[A](env: Map[String,Any])(r: Remote[A]): Task[A] = {
    import Remote._
    val T = Monad[Task]
    r match {
      case Async(a, _, _) => a
      case Local(a,_,_) => Task.now(a)
      case Ref(name) => env.lift(name) match {
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

  class Error(msg: String) extends Exception(msg)
}
