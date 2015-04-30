//: ----------------------------------------------------------------------------
//: Copyright (C) 2014 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------

package remotely

import remotely.Response.Context

import scala.collection.immutable.{IndexedSeq,Set,SortedMap,SortedSet}
import scala.math.Ordering
import scala.reflect.runtime.universe.TypeTag
import scalaz.{\/,-\/,\/-,Monad}
import scalaz.\/._
import scalaz.concurrent.Task
import scalaz.syntax.std.option._
import shapeless.Lazy
import scodec.{codecs => C, _}
import scodec.bits.BitVector
import scodec.interop.scalaz._
import Remote._

import scalaz.stream.Process
import utils._

private[remotely] trait lowerprioritycodecs {

  // Since `Codec[A]` extends `Encoder[A]`, which is contravariant in `A`,
  // and there are a few places where we ask for an implicit `Encoder[A]`,
  // we make this implicit lower priority to avoid ambiguous implicits.
  implicit def seq[A](implicit LCA: Lazy[Codec[A]]): Codec[Seq[A]] = C.variableSizeBytes(C.int32,
    C.vector(Codec[A]).xmap[Seq[A]](
      a => a,
      _.toVector
    ))
}

package object codecs extends lowerprioritycodecs {
  implicit val float = C.float
  implicit val double = C.double
  implicit val int32 = C.int32
  implicit val int64 = C.int64
  implicit val byte = C.byte
  implicit val utf8 = C.variableSizeBytes(int32, C.utf8)
  implicit val bool = C.bool(8) // use a full byte

  implicit def tuple2[A, B](implicit LCA: Lazy[Codec[A]], LCB: Lazy[Codec[B]]): Lazy[Codec[(A,B)]] =
    LCA.value ~ LCB.value

  implicit def either[A, B](implicit LCA: Lazy[Codec[A]], LCB: Lazy[Codec[B]]): Codec[A \/ B] =
    C.discriminated[A \/ B].by(bool)
    .| (false) { case -\/(l) => l } (\/.left) (Codec[A])
    .| (true)  { case \/-(r) => r } (\/.right) (Codec[B])

  implicit def stdEither[A, B](implicit LCA: Lazy[Codec[A]], LCB: Lazy[Codec[B]]): Codec[Either[A,B]] =
    C.either(bool, Codec[A], Codec[B])
  

  implicit def byteArray: Codec[Array[Byte]] = {
    val B = new Codec[Array[Byte]] {
      def sizeBound              = SizeBound.unknown
      def encode(b: Array[Byte]) = Attempt.successful(BitVector(b))
      def decode(b: BitVector)   = Attempt.successful(DecodeResult(b.toByteArray, BitVector.empty))
    }
    
    C.variableSizeBytes(int32, B)
  }

  implicit def set[A](implicit LCA: Lazy[Codec[A]]): Codec[Set[A]] =
    indexedSeq[A].xmap[Set[A]](
      s => Set(s: _*),
      _.toIndexedSeq)

  implicit def sortedSet[A](implicit LCA: Lazy[Codec[A]], O: Ordering[A]): Codec[SortedSet[A]] =
    indexedSeq[A].xmap[SortedSet[A]](
      s => SortedSet(s: _*),
      _.toIndexedSeq)

  private def empty: Codec[Unit] = C.provide(())

  def optional[A](target: Codec[A]): Codec[Option[A]] =
    either(empty, target).
      xmap[Option[A]](_.toOption, _.toRightDisjunction(()))

  implicit def list[A](implicit LCA: Lazy[Codec[A]]): Codec[List[A]] =
    indexedSeq[A].xmap[List[A]](
      _.toList,
      _.toIndexedSeq)

  implicit def indexedSeq[A](implicit LCA: Lazy[Codec[A]]): Codec[IndexedSeq[A]] =
    C.variableSizeBytes(int32, C.vector(Codec[A]).xmap(a => a, _.toVector))

  implicit def map[K, V](implicit LCK: Lazy[Codec[K]], LCV: Lazy[Codec[V]]): Codec[Map[K,V]] =
    indexedSeq[(K,V)].xmap[Map[K,V]](
      _.toMap, 
      _.toIndexedSeq
    )

  implicit def sortedMap[K: Ordering, V](implicit LCK: Lazy[Codec[K]], LCV: Lazy[Codec[V]]): Codec[SortedMap[K,V]] =
    indexedSeq[(K,V)].xmap[SortedMap[K,V]](
      kvs => SortedMap(kvs: _*), 
      _.toIndexedSeq
    )

  implicit class PlusSyntax(e: Attempt[BitVector]) {
    def <+>(r: => Attempt[BitVector]): Attempt[BitVector] =
      e.flatMap(bv => r.map(bv ++ _))
  }

  implicit def contextEncoder: Encoder[Response.Context] = new Encoder[Response.Context] {
    def sizeBound = SizeBound.unknown
    def encode(ctx: Response.Context) =
      map[String,String].encode(ctx.header) <+>
      list[String].encode(ctx.stack.map(_.toString))
  }
  
  implicit def contextDecoder: Decoder[Response.Context] = for {
    header <- map[String,String]
    stackS <- list[String]
    stack <- try succeed(stackS.map(Response.ID.fromString))
             catch { case e: IllegalArgumentException => fail(Err(s"[decoding] error decoding ID in tracing stack: ${e.getMessage}")) }
  } yield Response.Context(header, stack)

  def remoteEncode(r: Remote[Any]): Attempt[BitVector] =
    r match {
      case l: Local[_] => C.uint8.encode(0) <+> localRemoteEncoder.encode(l)
      case Async(a,e,t) =>
        Attempt.failure(Err("cannot encode Async constructor; call Remote.localize first"))
      case r: Ref[_] => C.uint8.encode(1) <+> refCodec.encode(r)
      case Ap1(f,a) => C.uint8.encode(2) <+>
        remoteEncode(f) <+> remoteEncode(a)
      case Ap2(f,a,b) => C.uint8.encode(3) <+>
        remoteEncode(f) <+> remoteEncode(a) <+> remoteEncode(b)
      case Ap3(f,a,b,c) => C.uint8.encode(4) <+>
        remoteEncode(f) <+> remoteEncode(a) <+> remoteEncode(b) <+> remoteEncode(c)
      case Ap4(f,a,b,c,d) => C.uint8.encode(5) <+>
        remoteEncode(f) <+> remoteEncode(a) <+> remoteEncode(b) <+> remoteEncode(c) <+> remoteEncode(d)
      case l: LocalStream[_] => C.uint8.encode(6) <+> localStreamRemoteEncoder.encode(l)
    }

  private val E = Monad[Decoder]

  def localRemoteEncoder[A] = new Encoder[Local[A]] {
    def encode(a: Local[A]): Attempt[BitVector] =
      a.format.map(encoder => utf8.encode(a.tag) <+> encoder.encode(a.a))
        .getOrElse(Attempt.failure(Err("cannot encode Local value with undefined encoder")))
    def sizeBound = SizeBound.unknown
  }

  def localRemoteDecoder(env: Codecs): Decoder[Local[Any]] =
    utf8.flatMap( formatType =>
      env.codecs.get(formatType).map{ codec => codec.map { a => Local(a,None,formatType) } }
        .getOrElse(fail(Err(s"[decoding] unknown format type: $formatType")))
    )

  def localStreamRemoteDecoder: Decoder[LocalStream[Any]] =
      utf8.map( tag => LocalStream(null, None, tag))

  def refCodec[A]: Codec[Ref[A]] = utf8.as[Ref[A]]

  /**
   * The LocalStream Encoder does not actually encode the stream which would be impossible
   * given the signature of decode on an Encoder anyway. It just informs the server side that
   * it will receive a stream after the transmission of the original request
   */
  def localStreamRemoteEncoder[A] = new Encoder[LocalStream[A]] {
    val sizeBound = utf8.sizeBound
    def encode(a: LocalStream[A]): Attempt[BitVector] =
      utf8.encode(a.tag)
  }

  /**
   * This function actually creates a stream of bits from a LocalStream to send to the
   * server once the initial request has been sent in it's entirety.
   */
  def encodeLocalStream[A](l: Option[LocalStream[A]]): Process[Task,BitVector] =
    l.map(l => l.format.map(encoder => l.stream.map(encoder.encode(_).toProcess))
      .getOrElse(Process.fail(Err("cannot encode Local value with undefined encoder"))).flatten).getOrElse(Process.empty)

  /**
   * A `Remote[Any]` decoder. If a `Local` value refers
   * to a decoder that is not found in `env`, decoding fails
   * with an error.
   */
  def remoteDecoder(env: Codecs): Decoder[Remote[Any]] = {
    def go = remoteDecoder(env)
    C.uint8.flatMap {
      case 0 => localRemoteDecoder(env)
      case 1 => refCodec
      case 2 => E.apply2(go,go)((f,a) =>
                  Ap1(f.asInstanceOf[Remote[Any => Any]],a))
      case 3 => E.apply3(go,go,go)((f,a,b) =>
                  Ap2(f.asInstanceOf[Remote[(Any,Any) => Any]],a,b))
      case 4 => E.apply4(go,go,go,go)((f,a,b,c) =>
                  Ap3(f.asInstanceOf[Remote[(Any,Any,Any) => Any]],a,b,c))
      case 5 => E.apply5(go,go,go,go,go)((f,a,b,c,d) =>
                  Ap4(f.asInstanceOf[Remote[(Any,Any,Any,Any) => Any]],a,b,c,d))
      case 6 => localStreamRemoteDecoder
      case t => fail(Err(s"[decoding] unknown tag byte: $t"))
    }
  }

  implicit def remoteEncoder[A]: Encoder[Remote[A]] =
    new Encoder[Remote[A]] {
      def sizeBound = SizeBound.unknown
      def encode(a: Remote[A]) = remoteEncode(a) 
    }

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
  def encodeRequest(a: Remote[Any], ctx: Context, remoteTag: String): Attempt[BitVector] =
    Codec[String].encode(remoteTag) <+>
    Encoder[Response.Context].encode(ctx) <+>
    sortedSet[String].encode(formats(a))  <+>
    remoteEncode(a)

  def requestDecoder(env: Environment): Decoder[(Encoder[Any],Response.Context,Remote[Any])] =
    for {
      responseTag <- utf8
      ctx <- Decoder[Response.Context]
      formatTags <- sortedSet[String]
      responseEncoder <- env.codecs.get(responseTag) match {
        case None => fail(Err(s"[decoding] server does not have response serializer for: $responseTag"))
        case Some(a) => succeed(a)
      }
      r <- {
        val unknown = (formatTags -- env.codecs.keySet).toList
        if (unknown.isEmpty) remoteDecoder(env.codecs)
        else {
          val unknownMsg = unknown.mkString("\n")
          fail(Err(s"[decoding] server does not have deserializers for:\n$unknownMsg"))
        }
      }
    } yield (responseEncoder, ctx, r)

  def responseDecoder[A](implicit LDA: Lazy[Decoder[A]]): Decoder[String \/ A] = bool flatMap {
    case false => utf8.map(left)
    case true => Decoder[A].map(right)
  }

  def responseEncoder[A](implicit LEA: Lazy[Encoder[A]]) = new Encoder[Attempt[A]] {
    def sizeBound = SizeBound.unknown

    def encode(a: Attempt[A]): Attempt[BitVector] =
      a.fold(s => bool.encode(false) <+> utf8.encode(s.messageWithContext),
             a => bool.encode(true) <+> Encoder[A].encode(a))
  }

  def fail[A](msg: Err): Decoder[A] = 
    new Decoder[A] {
      def decode(bits: BitVector) = Attempt.failure(msg)
    }.asInstanceOf[Decoder[A]]
  
  def succeed[A](a: A): Decoder[A] = C.provide(a)
}
