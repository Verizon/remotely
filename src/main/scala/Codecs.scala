package srpc

import scala.collection.immutable.{IndexedSeq,Set,SortedMap,SortedSet}
import scala.math.Ordering
import scala.reflect.runtime.universe.TypeTag
import scalaz.{\/,-\/,\/-,Monad}
import scalaz.\/._
import scalaz.concurrent.Task
import scodec.{Codec,codecs => C,Decoder,Encoder}
import C.Discriminator
import scodec.bits.BitVector
import Remote._

object Codecs {

  implicit val float = C.float
  implicit val double = C.double
  implicit val int32 = C.int32
  implicit val int64 = C.int64
  implicit val utf8 = C.utf8
  implicit val bool = C.bool

  implicit def tuple2[A:Codec,B:Codec]: Codec[(A,B)] =
    Codec[A] ~ Codec[B]

  implicit def either[A:Codec,B:Codec]: Codec[A \/ B] =
    C.discriminated[A \/ B].by(bool).using(
      Discriminator(
        { case -\/(a) => false
          case \/-(b) => true
        }: PartialFunction[A \/ B, Boolean],
        {
          case false => Codec[A].xmap[A \/ B](
            a => left(a),
            _.swap.getOrElse(sys.error("unpossible")))
          case true => Codec[B].xmap[A \/ B](
            b => right(b),
            _.getOrElse(sys.error("unpossible")))
        }: PartialFunction[Boolean, Codec[A \/ B]]
      )
    )

/*
// not published yet
  implicit def disjunction[A:Codec,B:Codec]: Codec[A \/ B] =
    new EitherCodec(bool, Codec[A], Codec[B])

  implicit def either[A:Codec,B:Codec]: Codec[Either[A,B]] =
    disjunction.xmap[Either[A,B]](_.toEither, \/.fromEither)
*/

  implicit def byteArray: Codec[Array[Byte]] = ???

  implicit def seq[A:Codec]: Codec[Seq[A]] =
    C.repeated(Codec[A]).xmap[Seq[A]](
      a => a,
      _.toIndexedSeq
    )
  implicit def sortedSet[A:Codec:Ordering]: Codec[SortedSet[A]] =
    indexedSeq[A].xmap[SortedSet[A]](
      s => SortedSet(s: _*),
      _.toIndexedSeq)

  implicit def set[A:Codec]: Codec[Set[A]] =
    indexedSeq[A].xmap[Set[A]](
      s => Set(s: _*),
      _.toIndexedSeq)

  implicit def indexedSeq[A:Codec]: Codec[IndexedSeq[A]] =
    C.repeated(Codec[A])

  implicit def list[A:Codec]: Codec[List[A]] =
    indexedSeq[A].xmap[List[A]](
      _.toList,
      _.toIndexedSeq)

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

  def remoteEncode[A](r: Remote[A]): String \/ BitVector =
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

  private val E = Monad[Decoder]

  /**
   * A `Remote[Any]` decoder. If a `Local` value refers
   * to a decoder that is not found in `env`, decoding fails
   * with an error.
   */
  def remoteDecoder(env: Map[String,Decoder[Any]]): Decoder[Remote[Any]] = {
    lazy val go = remoteDecoder(env)
    C.uint8.flatMap {
      case 0 => C.utf8.flatMap { fmt =>
                  env.get(fmt) match {
                    case None => fail(s"[decoding] unknown format type: $fmt")
                    case Some(dec) => dec.map { a => Local(a,None,fmt) }
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
  def encodeRequest[A:TypeTag](r: Remote[A]): Task[BitVector] =
    localize(r).flatMap { a =>
      Codec[String].encode(Remote.toTag[A]) <+>
      sortedSet[String].encode(formats(a)) <+>
      remoteEncode(a) fold (
        err => Task.fail(new EncodingFailure(err)),
        bits => Task.now(bits)
      )
    }

  def requestDecoder(env: Environment): Decoder[(Encoder[Any],Remote[Any])] =
    for {
      responseTag <- utf8
      formatTags <- sortedSet[String]
      r <- {
        val unknown = ((formatTags + responseTag) -- env.decoders.keySet).toList
        if (unknown.isEmpty) remoteDecoder(env.decoders)
        else fail(s"[decoding] server does not have deserializers for: $unknown")
      }
      responseDec <- env.encoders.get(responseTag) match {
        case None => fail(s"[decoding] server does not have response serializer for:$responseTag")
        case Some(a) => succeed(a)
      }
    } yield (responseDec, r)

  def responseCodec[A:Codec] = either[String,A]

  def fail(msg: String): Decoder[Nothing] =
    new Decoder[Nothing] { def decode(bits: BitVector) = left(msg) }

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

  implicit def codecAsEncoder[A:Codec]: Encoder[A] =
    new Encoder[A] { def encode(a: A) = Codec[A].encode(a) }

  implicit def codecAsDecoder[A:Codec]: Decoder[A] =
    new Decoder[A] { def decode(bits: BitVector) = Codec[A].decode(bits) }

  class EncodingFailure(msg: String) extends Exception(msg)
  class DecodingFailure(msg: String) extends Exception(msg)

}
