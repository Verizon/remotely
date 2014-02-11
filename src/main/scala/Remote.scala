package srpc

import scalaz.concurrent.Task
import scalaz.{\/, Monad}
import scala.reflect.ClassManifest
import scodec.{Codec,codecs => C,Decoder,Encoder,Error}
import scodec.codecs.Discriminator
import scodec.bits.BitVector
import shapeless._

/**
 * Represents a remote computation which yields a
 * value of type `A`.
 */
sealed trait Remote[+A]

object Remote {

  /** Promote a local value to a remote value. */
  private[srpc] case class Local[A](
    a: A, // the value
    format: Codec[A], // serializer for `A`
    tag: String // identifies the deserializer to be used by server
  ) extends Remote[A]
  implicit def localIso = Iso.hlist(Local.apply _, Local.unapply _)


  /** Promote an asynchronous task to a remote value. */
  private[srpc] case class Async[A](
    a: Task[A],
    format: Codec[A], // serializer for `A`
    tag: String // identifies the deserializer to be used by server
  ) extends Remote[A]
  implicit def asyncIso = Iso.hlist(Async.apply _, Async.unapply _)

  /**
   * Reference to a remote value on the server.
   */
  private[srpc] case class Ref[A](name: String) extends Remote[A]
  implicit def refIso = Iso.hlist(Ref.apply _, Ref.unapply _)

  // we require a separate constructor for each function
  // arity, since remote invocations must be fully saturated
  private[srpc] case class Ap1[A,B](
    f: Remote[A => B],
    a: Remote[A]) extends Remote[B]
  implicit def ap1Iso = Iso.hlist(Ap1.apply _, Ap1.unapply _)

  private[srpc] case class Ap2[A,B,C](
    f: Remote[(A,B) => C],
    a: Remote[A],
    b: Remote[B]) extends Remote[C]
  implicit def ap2Iso = Iso.hlist(Ap2.apply _, Ap2.unapply _)

  private[srpc] case class Ap3[A,B,C,D](
    f: Remote[(A,B,C) => D],
    a: Remote[A],
    b: Remote[B],
    c: Remote[C]) extends Remote[D]
  implicit def ap3Iso = Iso.hlist(Ap3.apply _, Ap3.unapply _)

  private[srpc] case class Ap4[A,B,C,D,E](
    f: Remote[(A,B,C,D) => E],
    a: Remote[A],
    b: Remote[B],
    c: Remote[C],
    d: Remote[D]) extends Remote[E]
  implicit def ap4Iso = Iso.hlist(Ap4.apply _, Ap4.unapply _)

  val T = Monad[Task]

  /**
   * Precursor to serializing a remote computation
   * to send to server for evaluation. This function
   * removes all `Async` constructors.
   */
  def localize[A](r: Remote[A]): Task[Remote[A]] = r match {
    case Async(a,c,t) => a.map { a => Local(a,c.asInstanceOf[Codec[A]],t) }
    case Ap1(f,a) => T.apply2(localize(f), localize(a))(Ap1.apply)
    case Ap2(f,a,b) => T.apply3(localize(f), localize(a), localize(b))(Ap2.apply)
    case Ap3(f,a,b,c) => T.apply4(localize(f), localize(a), localize(b), localize(c))(Ap3.apply)
    case Ap4(f,a,b,c,d) => T.apply5(localize(f), localize(a), localize(b), localize(c), localize(d))(Ap4.apply)
    case _ => Task.now(r) // Ref or Local
  }

  import scalaz.\/._
  implicit class PlusSyntax(e: Error \/ BitVector) {
    def <+>(r: => Error \/ BitVector): Error \/ BitVector =
      e.flatMap(bv => r.map(bv ++ _))
  }

  def remoteEncode[A](r: Remote[A]): Error \/ BitVector =
    r match {
      case Local(a,e,t) => C.uint8.encode(0) <+> // tag byte
        C.utf8.encode(t) <+> e.asInstanceOf[Codec[A]].encode(a)
      case Async(a,e,t) =>
        left("cannot encode Async constructor; call Remote.localize first")
      case Ref(t) => C.uint8.encode(1) <+>
        C.utf8.encode(t)
      case Ap1(f,a) => C.uint8.encode(2) <+>
        remoteEncode(f) <+> remoteEncode(a)
      case Ap2(f,a,b) => C.uint8.encode(3) <+>
        remoteEncode(f) <+> remoteEncode(a) <+> remoteEncode(b)
      case Ap3(f,a,b,c) => C.uint8.encode(4) <+>
        remoteEncode(f) <+> remoteEncode(a) <+> remoteEncode(b) <+> remoteEncode(c)
      case Ap4(f,a,b,c,d) => C.uint8.encode(5) <+>
        remoteEncode(f) <+> remoteEncode(a) <+> remoteEncode(b) <+> remoteEncode(c) <+> remoteEncode(d)
    }

  val E = Monad[Decoder]

  /**
   * A `Remote[Any]` decoder. If a `Local` value refers
   * to a decoder that is not found in `env`, decoding fails
   * with an error.
   */
  def remoteDecoder(env: Map[String,Codec[Any]]): Decoder[Remote[Any]] = {
    lazy val go = remoteDecoder(env)
    C.uint8.flatMap {
      case 0 => C.utf8.flatMap { fmt =>
                  env.get(fmt) match {
                    case None => fail(s"[decoding]: unknown format type: $fmt")
                    case Some(codec) => codec.map { a => Local(a,codec,fmt) }
                  }
                }
      case 1 => C.utf8.map(Ref.apply)
      case 2 => E.apply2(go,go)((f,a) =>
                  Ap1(f.asInstanceOf[Remote[Any => Any]],a))
      case 3 => E.apply3(go,go,go)((f,a,b) =>
                  Ap2(f.asInstanceOf[Remote[(Any,Any) => Any]],a,b))
      case 4 => E.apply4(go,go,go,go)((f,a,b,c) =>
                  Ap3(f.asInstanceOf[Remote[(Any,Any,Any) => Any]],a,b,c))
      case 5 => E.apply5(go,go,go,go,go)((f,a,b,c,d) =>
                  Ap4(f.asInstanceOf[Remote[(Any,Any,Any,Any) => Any]],a,b,c,d))
      case t => fail(s"unknown tag byte: $t")
    }
  }

  implicit def remoteEncoder[A]: Encoder[Remote[A]] =
    new Encoder[Remote[A]] { def encode(a: Remote[A]) = remoteEncode(a) }

  def fail(msg: String): Decoder[Nothing] =
    new Decoder[Nothing] { def decode(bits: BitVector) = left(msg) }

  /**
   * Wait for all `Async` tasks to complete, then encode
   * the remaining concrete expression. The produced
   * bit vector may be read by `remoteDecoder`. That is,
   * `encode(r).flatMap(bits => remoteDecoder(env).decode(bits))`
   * should succeed, given a suitable `env` which knows how
   * to decode the serialized values.
   *
   * Use `encode(r).map(_.toByteArray)` to produce a `Task[Array[Byte]]`.
   */
  def encode[A](r: Remote[A]): Task[BitVector] =
    localize(r).flatMap { a =>
      remoteEncode(a).fold(
        err => Task.fail(new EncodingFailure(err)),
        bits => Task.now(bits)
      )
    }

  class EncodingFailure(msg: String) extends Exception(msg)
}
