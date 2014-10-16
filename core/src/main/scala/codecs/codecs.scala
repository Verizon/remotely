package remotely

import scala.collection.immutable.{IndexedSeq,Set,SortedMap,SortedSet}
import scala.math.Ordering
import scala.reflect.runtime.universe.TypeTag
import scalaz.{\/,-\/,\/-,Monad}
import scalaz.\/._
import scalaz.concurrent.Task
import scalaz.syntax.std.option._
import scodec.{Codec,codecs => C,Decoder,Encoder}
import scodec.bits.BitVector
import Remote._

private[remotely] trait lowerprioritycodecs {

  // Since `Codec[A]` extends `Encoder[A]`, which is contravariant in `A`,
  // and there are a few places where we ask for an implicit `Encoder[A]`,
  // we make this implicit lower priority to avoid ambiguous implicits.
  implicit def seq[A:Codec]: Codec[Seq[A]] = C.variableSizeBytes(C.int32,
    C.repeated(Codec[A]).xmap[Seq[A]](
      a => a,
      _.toIndexedSeq
    ))
}

package object codecs extends lowerprioritycodecs with TupleHelpers {

  implicit val float = C.float
  implicit val double = C.double
  implicit val int32 = C.int32
  implicit val int64 = C.int64
  implicit val utf8 = C.variableSizeBytes(int32, C.utf8)
  implicit val bool = C.bool(8) // use a full byte

  implicit def tuple2[A:Codec,B: Codec]: Codec[(A,B)] =
    Codec[A] ~ Codec[B]

  implicit def either[A:Codec,B:Codec]: Codec[A \/ B] =
    C.either(bool, Codec[A], Codec[B])

  implicit def stdEither[A:Codec,B:Codec]: Codec[Either[A,B]] =
    C.stdEither(bool, Codec[A], Codec[B])

  implicit def byteArray: Codec[Array[Byte]] = {
    val B = new Codec[Array[Byte]] {
      def encode(b: Array[Byte]): String \/ BitVector = right(BitVector(b))
      def decode(b: BitVector): String \/ (BitVector, Array[Byte]) = right(BitVector.empty -> b.toByteArray)
    }
    C.variableSizeBytes(int32, B)
  }

  implicit def set[A:Codec]: Codec[Set[A]] =
    indexedSeq[A].xmap[Set[A]](
      s => Set(s: _*),
      _.toIndexedSeq)

  implicit def sortedSet[A:Codec:Ordering]: Codec[SortedSet[A]] =
    indexedSeq[A].xmap[SortedSet[A]](
      s => SortedSet(s: _*),
      _.toIndexedSeq)

  private def empty: Codec[Unit] = new Codec[Unit] {
    override def encode(ign: Unit) = \/.right(BitVector.empty)
    override def decode(bits: BitVector) = \/.right((bits,()))
  }

  def optional[A](target: Codec[A]): Codec[Option[A]] =
    either(empty, target).
      xmap[Option[A]](_.toOption, _.toRightDisjunction(()))

  implicit def list[A:Codec]: Codec[List[A]] =
    indexedSeq[A].xmap[List[A]](
      _.toList,
      _.toIndexedSeq)

  implicit def indexedSeq[A:Codec]: Codec[IndexedSeq[A]] =
    C.variableSizeBytes(int32, C.repeated(Codec[A]))

  implicit def map[K:Codec,V:Codec]: Codec[Map[K,V]] =
    indexedSeq[(K,V)].xmap[Map[K,V]](
      _.toMap,
      _.toIndexedSeq
    )

  implicit def sortedMap[K:Codec:Ordering,V:Codec]: Codec[SortedMap[K,V]] =
    indexedSeq[(K,V)].xmap[SortedMap[K,V]](
      kvs => SortedMap(kvs: _*),
      _.toIndexedSeq
    )

  import scalaz.\/._
  implicit class PlusSyntax(e: String \/ BitVector) {
    def <+>(r: => String \/ BitVector): String \/ BitVector =
      e.flatMap(bv => r.map(bv ++ _))
  }

  implicit def contextEncoder: Encoder[Response.Context] = new Encoder[Response.Context] {
    def encode(ctx: Response.Context) =
      map[String,String].encode(ctx.header) <+>
      list[String].encode(ctx.stack.map(_.toString))
  }
  implicit def contextDecoder: Decoder[Response.Context] = for {
    header <- map[String,String]
    stackS <- list[String]
    stack <- try succeed(stackS.map(Response.ID.fromString))
             catch { case e: IllegalArgumentException => fail(s"[decoding] error decoding ID in tracing stack: ${e.getMessage}") }
  } yield Response.Context(header, stack)

  def remoteEncode[A](r: Remote[A]): String \/ BitVector =
    r match {
      case Local(a,e,t) => C.uint8.encode(0) <+> // tag byte
        utf8.encode(t) <+>
        e.asInstanceOf[Option[Encoder[A]]].map(_.encode(a))
          .getOrElse(left("cannot encode Local value with undefined encoder"))
      case Async(a,e,t) =>
        left("cannot encode Async constructor; call Remote.localize first")
      case Ref(t) => C.uint8.encode(1) <+>
        utf8.encode(t)
      case Ap1(f,a) => C.uint8.encode(2) <+>
        remoteEncode(f) <+> remoteEncode(a)
      case Ap2(f,a,b) => C.uint8.encode(3) <+>
        remoteEncode(f) <+> remoteEncode(a) <+> remoteEncode(b)
      case Ap3(f,a,b,c) => C.uint8.encode(4) <+>
        remoteEncode(f) <+> remoteEncode(a) <+> remoteEncode(b) <+> remoteEncode(c)
      case Ap4(f,a,b,c,d) => C.uint8.encode(5) <+>
        remoteEncode(f) <+> remoteEncode(a) <+> remoteEncode(b) <+> remoteEncode(c) <+> remoteEncode(d)
    }

  private val E = Monad[Decoder]

  /**
   * A `Remote[Any]` decoder. If a `Local` value refers
   * to a decoder that is not found in `env`, decoding fails
   * with an error.
   */
  def remoteDecoder(env: Codecs): Decoder[Remote[Any]] = {
    lazy val go = remoteDecoder(env)
    C.uint8.flatMap {
      case 0 =>
        utf8.flatMap { fmt =>
                    env.codecs.get(fmt) match {
                      case None => fail(s"[decoding] unknown format type: $fmt")
                      case Some(dec) => dec.map { a => Local(a,None,fmt) }
                    }
                  }
      case 1 =>
        utf8.map(Ref.apply)
      case 2 => E.apply2(go,go)((f,a) =>
                  Ap1(f.asInstanceOf[Remote[Any => Any]],a))
      case 3 => E.apply3(go,go,go)((f,a,b) =>
                  Ap2(f.asInstanceOf[Remote[(Any,Any) => Any]],a,b))
      case 4 => E.apply4(go,go,go,go)((f,a,b,c) =>
                  Ap3(f.asInstanceOf[Remote[(Any,Any,Any) => Any]],a,b,c))
      case 5 => E.apply5(go,go,go,go,go)((f,a,b,c,d) =>
                  Ap4(f.asInstanceOf[Remote[(Any,Any,Any,Any) => Any]],a,b,c,d))
      case t => fail(s"[decoding] unknown tag byte: $t")
    }
  }

  implicit def remoteEncoder[A]: Encoder[Remote[A]] =
    new Encoder[Remote[A]] { def encode(a: Remote[A]) = remoteEncode(a) }

  /**
   * Wait for all `Async` tasks to complete, then encode
   * the remaining concrete expression. The produced
   * bit vector may be read by `remoteDecoder`. That is,
   * `encodeRequest(r).flatMap(bits => decodeRequest(env).decode(bits))`
   * should succeed, given a suitable `env` which knows how
   * to decode the serialized values.
   *
   * Use `encode(r).map(_.toByteArray)` to produce a `Task[Array[Byte]]`.
   */
  def encodeRequest[A:TypeTag](a: Remote[A]): Response[BitVector] = Response { ctx =>
    Codec[String].encode(Remote.toTag[A]) <+>
    Encoder[Response.Context].encode(ctx) <+>
    sortedSet[String].encode(formats(a))  <+>
    remoteEncode(a) fold (
      err => Task.fail(new EncodingFailure(err)),
      bits => Task.now(bits)
    )
  }



  def requestDecoder(env: Environment): Decoder[(Encoder[Any],Response.Context,Remote[Any])] =
    for {
      responseTag <- utf8
      ctx <- Decoder[Response.Context]
      formatTags <- sortedSet[String]
      r <- {
        val unknown = ((formatTags + responseTag) -- env.codecs.keySet).toList
        if (unknown.isEmpty) remoteDecoder(env.codecs)
        else {
          val unknownMsg = unknown.mkString("\n")
          fail(s"[decoding] server does not have deserializers for:\n$unknownMsg")
        }
      }
      responseDec <- env.codecs.get(responseTag) match {
        case None => fail(s"[decoding] server does not have response serializer for: $responseTag")
        case Some(a) => succeed(a)
      }
    } yield (responseDec, ctx, r)

  def responseCodec[A:Codec] = either[String,A]

  def responseDecoder[A:Decoder]: Decoder[String \/ A] = bool flatMap {
    case false => utf8.map(left)
    case true => Decoder[A].map(right)
  }

  def responseEncoder[A:Encoder] = new Encoder[String \/ A] {
    def encode(a: String \/ A): String \/ BitVector =
      a.fold(s => bool.encode(false) <+> utf8.encode(s),
             a => bool.encode(true) <+> Encoder[A].encode(a))
  }

  def fail[A](msg: String): Decoder[A] =
    new Decoder[A] { def decode(bits: BitVector) = left(msg) }.asInstanceOf[Decoder[A]]

  def succeed[A](a: A): Decoder[A] = C.provide(a)

  def decodeTask[A:Decoder](bits: BitVector): Task[A] =
    liftDecode(Decoder[A].decode(bits))

  def liftEncode(result: String \/ BitVector): Task[BitVector] =
    result.fold(
      e => Task.fail(new EncodingFailure(e)),
      bits => Task.now(bits)
    )
  def liftDecode[A](result: String \/ (BitVector,A)): Task[A] =
    result.fold(
      e => Task.fail(new DecodingFailure(e)),
      { case (trailing,a) =>
        if (trailing.isEmpty) Task.now(a)
        else Task.fail(new DecodingFailure("trailing bits: " + trailing)) }
    )

  // borrowed from scodec 1.3, lets you exmap to a disjucntion where the left fails the encode/decode
  def exmap[A,B](self: Codec[A])(f: A => String \/ B, g: B => String \/ A): Codec[B] = new Codec[B] {
     def encode(b: B): String \/ BitVector = g(b) flatMap self.encode
     def decode(buffer: BitVector): String \/ (BitVector, B) =
       self.decode(buffer) flatMap { case (rest, a) => f(a).flatMap { b => \/.right((rest, b)) } }
  }
}

trait TupleHelpers {
  implicit class BedazzledCodec[A](a: Codec[A]) {
    def ~~[B](b: Codec[B]): Tuple2Codec[A,B] =
      new Tuple2Codec[A,B](a ~ b)
  }

  class Tuple2Codec[A,B](ab: Codec[(A,B)]) {
    def ~~[C](c: Codec[C]): Tuple3Codec[A,B,C] = new Tuple3Codec(ab ~ c)

    def pxmap[X](to: (A,B) => X,
                 from: X => Option[(A,B)]) = ab.pxmap(to.tupled, from)
  }

  class Tuple3Codec[A,B,C](abc: Codec[((A,B),C)]) {
    def ~~[D](d: Codec[D]): Tuple4Codec[A,B,C,D] = new Tuple4Codec(abc ~ d)

    def pxmap[X](to: (A,B,C) => X,
                 from: X => Option[(A,B,C)]) = abc.pxmap(
      { case ((a,b),c) => to(a,b,c) },
      from andThen { _.map(abc => ((abc._1, abc._2), abc._3)) }
    )
  }

  class Tuple4Codec[A,B,C,D](abcd: Codec[(((A,B),C),D)]) {
    def ~~[E](e: Codec[E]): Tuple5Codec[A,B,C,D,E] = new Tuple5Codec(abcd ~ e)

    def pxmap[X](to: (A,B,C,D) => X,
                 from: X => Option[(A,B,C,D)]) = abcd.pxmap(
      { case (((a,b),c),d) => to(a,b,c,d) },
      from andThen { _.map(abcd => (((abcd._1, abcd._2), abcd._3), abcd._4)) }
    )
  }

  class Tuple5Codec[A,B,C,D,E](abcde: Codec[((((A,B),C),D),E)]) {
    def ~~[F](f: Codec[F]): Tuple6Codec[A,B,C,D,E,F] = new Tuple6Codec(abcde ~ f)

    def pxmap[X](to: (A,B,C,D,E) => X,
                 from: X => Option[(A,B,C,D,E)]) = abcde.pxmap(
      { case ((((a,b),c),d),e) => to(a,b,c,d,e) },
      from andThen { _.map(abcde => ((((abcde._1, abcde._2), abcde._3), abcde._4),abcde._5)) }
    )
  }

  class Tuple6Codec[A,B,C,D,E,F](abcdef: Codec[(((((A,B),C),D),E),F)]) {
    def ~~[G](g: Codec[G]): Tuple7Codec[A,B,C,D,E,F,G] = new Tuple7Codec(abcdef ~ g)

    def pxmap[X](to: (A,B,C,D,E,F) => X,
                 from: X => Option[(A,B,C,D,E,F)]) = abcdef.pxmap(
      { case (((((a,b),c),d),e),f) => to(a,b,c,d,e,f) },
      from andThen { _.map(abcdef => (((((abcdef._1, abcdef._2), abcdef._3), abcdef._4),abcdef._5),abcdef._6)) }
    )
  }

  class Tuple7Codec[A,B,C,D,E,F,G](abcdefg: Codec[((((((A,B),C),D),E),F),G)]) {
    def pxmap[X](to: (A,B,C,D,E,F,G) => X,
                 from: X => Option[(A,B,C,D,E,F,G)]) = abcdefg.pxmap(
      { case ((((((a,b),c),d),e),f),g) => to(a,b,c,d,e,f,g) },
      from andThen { _.map(abcdefg => ((((((abcdefg._1, abcdefg._2), abcdefg._3), abcdefg._4),abcdefg._5),abcdefg._6),abcdefg._7)) }
    )
  }
}
