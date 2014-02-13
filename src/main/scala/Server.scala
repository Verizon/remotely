package srpc

import scalaz.{\/,Monad}
import scalaz.stream.Process
import scalaz.concurrent.Task
import scodec.bits.BitVector
import scodec.Codec

object Server {

  /**
   * Handle a single RPC request, given a decoding
   * environment and a values environment.
   */
  def handle(decoders: Map[String,Codec[Any]],
             values: Map[String,Any])(
             request: BitVector): Task[BitVector] = {
    Codecs.requestDecoder(decoders).flatMap { r =>
      val expected = Remote.refs(r)
      val unknown = (expected -- values.keySet).toList
      if (unknown.isEmpty) Codecs.succeed(r)
      else Codecs.fail(s"[validation] server does not have referenced values: $unknown")
    }.decode(request).fold(
      e => Task.fail(new Error(e)),
      { case (trailing, r) =>
         if (trailing.isEmpty) eval(values)(r).flatMap { a =>
           ???
           // well, shit - not enough info in the Remote ADT to
           // know how to deserialize the result! need a tag for
           // each function application, it appears
           // toTask(Codecs.remoteEncode(a))
         }
         else Task.fail(new Error("[validation] trailing bytes in request: "+trailing))
      }
    )
  }

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
