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
import scodec.{Codec,codecs => C,Decoder,Encoder}
import scodec.bits.BitVector
import Remote._
import scodec.Err

private[remotely] trait lowerprioritycodecs {

  // Since `Codec[A]` extends `Encoder[A]`, which is contravariant in `A`,
  // and there are a few places where we ask for an implicit `Encoder[A]`,
  // we make this implicit lower priority to avoid ambiguous implicits.
  implicit def seq[A:Codec]: Codec[Seq[A]] = C.variableSizeBytes(C.int32,
    C.vector(Codec[A]).xmap[Seq[A]](
      a => a,
      _.toVector
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
      def encode(b: Array[Byte]): Err \/ BitVector = right(BitVector(b))
      def decode(b: BitVector): Err \/ (BitVector, Array[Byte]) = right(BitVector.empty -> b.toByteArray)
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
    C.variableSizeBytes(int32, C.vector(Codec[A]).xmap(a => a, _.toVector))

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

  implicit class PlusSyntax(e: Err \/ BitVector) {
    def <+>(r: => Err \/ BitVector): Err \/ BitVector =
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
             catch { case e: IllegalArgumentException => fail(Err(s"[decoding] error decoding ID in tracing stack: ${e.getMessage}")) }
  } yield Response.Context(header, stack)

  def remoteEncode[A](r: Remote[A]): Err \/ BitVector =
    r match {
      case l: Local[A] => C.uint8.encode(0) <+> localRemoteEncoder.encode(l)
      case Async(a,e,t) =>
        left(Err("cannot encode Async constructor; call Remote.localize first"))
      case r: Ref[A] => C.uint8.encode(1) <+> refCodec.encode(r)
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

  def localRemoteEncoder[A] = new Encoder[Local[A]] {
    def encode(a: Local[A]): Err \/ BitVector =
      a.format.map(encoder => utf8.encode(a.tag) <+> encoder.encode(a.a))
        .getOrElse(left(Err("cannot encode Local value with undefined encoder")))
  }

  def localRemoteDecoder(env: Codecs): Decoder[Local[Any]] =
    utf8.flatMap( formatType =>
      env.codecs.get(formatType).map{ codec => codec.map { a => Local(a,None,formatType) } }
        .getOrElse(fail(Err(s"[decoding] unknown format type: $formatType")))
    )

  def refCodec[A]: Codec[Ref[A]] = utf8.as[Ref[A]]

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
      case t => fail(Err(s"[decoding] unknown tag byte: $t"))
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
  def encodeRequest[A:TypeTag](a: Remote[A], ctx: Context): Err \/ BitVector =
    Codec[String].encode(Remote.toTag[A]) <+>
    Encoder[Response.Context].encode(ctx) <+>
    sortedSet[String].encode(formats(a))  <+>
    remoteEncode(a)

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
          fail(Err(s"[decoding] server does not have deserializers for:\n$unknownMsg"))
        }
      }
      responseDec <- env.codecs.get(responseTag) match {
        case None => fail(Err(s"[decoding] server does not have response serializer for: $responseTag"))
        case Some(a) => succeed(a)
      }
    } yield (responseDec, ctx, r)

  def responseDecoder[A:Decoder]: Decoder[String \/ A] = bool flatMap {
    case false => utf8.map(left)
    case true => Decoder[A].map(right)
  }

  def responseEncoder[A:Encoder] = new Encoder[Err \/ A] {
    def encode(a: Err \/ A): Err \/ BitVector =
      a.fold(s => bool.encode(false) <+> utf8.encode(s.messageWithContext),
             a => bool.encode(true) <+> Encoder[A].encode(a))
  }

  def fail[A](msg: Err): Decoder[A] =
    new Decoder[A] { def decode(bits: BitVector) = left(msg) }.asInstanceOf[Decoder[A]]

  def succeed[A](a: A): Decoder[A] = C.provide(a)
}

trait TupleHelpers {
  implicit class BedazzledCodec[A](a: Codec[A]) {
    def ~~[B](b: Codec[B]): Tuple2Codec[A,B] =
      new Tuple2Codec[A,B](a, b)
  }

  class Tuple2Codec[A,B](A: Codec[A], B: Codec[B]) extends Codec[(A,B)] {
    def ~~[C](C: Codec[C]): Tuple3Codec[A,B,C] = new Tuple3Codec(A,B,C)

    override def decode(bits: BitVector): Err \/ (BitVector, (A,B)) = {
      for {
        aa <- A.decode(bits).leftMap(e => Err(s"tuple2-1 from ${bits.size} bits -- " + e.messageWithContext))
                      (bits1,a) = aa
        bb <- B.decode(bits1).leftMap(e => Err(s"tuple2-2 from ${bits1.size} bits -- " + e.messageWithContext))
      } yield(bb._1, (a,bb._2))

    }

    override def encode(ab: (A,B)): Err \/ BitVector =
      for {
        bits <- A.encode(ab._1)
        bits2 <- B.encode(ab._2)
      } yield bits ++ bits2

    def pxmap[X](to: (A,B) => X, from: X => Option[(A,B)]): Codec[X] = this.pxmap(to.tupled, from)
  }

  class Tuple3Codec[A,B,C](A: Codec[A], B: Codec[B], C: Codec[C]) extends Codec[(A,B,C)] {
    def ~~[D](D: Codec[D]): Tuple4Codec[A,B,C,D] = new Tuple4Codec(A,B,C,D)

    override def decode(bits: BitVector): Err \/ (BitVector, (A,B,C)) = {
      val x = for {
        aa <- A.decode(bits)
                      (bits1,a) = aa
        bb <- B.decode(bits1)
                      (bits2,b) = bb
        cc <- C.decode(bits2)
      } yield(cc._1, (a,b,cc._2))
      x.leftMap(e => Err(s"tuple3 from ${bits.size} bits -- " + e.messageWithContext))
    }

    override def encode(abc: (A,B,C)): Err \/ BitVector =
      for {
        bits <- A.encode(abc._1)
        bits2 <- B.encode(abc._2)
        bits3 <- C.encode(abc._3)
      } yield bits ++ bits2 ++ bits3

    def pxmap[X](to: (A,B,C) => X, from: X => Option[(A,B,C)]): Codec[X] = this.pxmap(to.tupled, from)
  }

  class Tuple4Codec[A,B,C,D](A: Codec[A], B: Codec[B], C: Codec[C], D: Codec[D]) extends Codec[(A,B,C,D)] {
    def ~~[E](E: Codec[E]): Tuple5Codec[A,B,C,D,E] = new Tuple5Codec(A,B,C,D,E)

    override def decode(bits: BitVector): Err \/ (BitVector, (A,B,C,D)) = {
      val x = for {
        aa <- A.decode(bits)
                      (bits1,a) = aa
        bb <- B.decode(bits1)
                      (bits2,b) = bb
        cc <- C.decode(bits2)
                      (bits3,c) = cc
        dd <- D.decode(bits3)
      } yield (dd._1, (a,b,c,dd._2))

      x.leftMap(e => Err(s"tuple4 from ${bits.size} bits -- " + e.messageWithContext))
    }
    override def encode(abcd: (A,B,C,D)): Err \/ BitVector =
      for {
        bits <- A.encode(abcd._1)
        bits2 <- B.encode(abcd._2)
        bits3 <- C.encode(abcd._3)
        bits4 <- D.encode(abcd._4)
      } yield bits ++ bits2 ++ bits3 ++ bits4


    def pxmap[X](to: (A,B,C,D) => X, from: X => Option[(A,B,C,D)]): Codec[X] = this.pxmap(to.tupled,from)
  }

  class Tuple5Codec[A,B,C,D,E](A: Codec[A], B: Codec[B], C: Codec[C], D: Codec[D], E: Codec[E]) extends Codec[(A,B,C,D,E)] {
    def ~~[F](F: Codec[F]): Tuple6Codec[A,B,C,D,E,F] = new Tuple6Codec(A,B,C,D,E,F)

    override def decode(bits: BitVector): Err \/ (BitVector, (A,B,C,D,E)) = {
      val x = for {
        aa <- A.decode(bits)
                      (bits1,a) = aa
        bb <- B.decode(bits1)
                      (bits2,b) = bb
        cc <- C.decode(bits2)
                      (bits3,c) = cc
        dd <- D.decode(bits3)
                      (bits4,d) = dd
        ee <- E.decode(bits4)
      } yield (ee._1, (a,b,c,d,ee._2))
      x.leftMap(e => Err(s"tuple4 from ${bits.size} bits -- " + e.messageWithContext))
    }


    override def encode(abcde: (A,B,C,D,E)): Err \/ BitVector =
      for {
        bits <- A.encode(abcde._1)
        bits2 <- B.encode(abcde._2)
        bits3 <- C.encode(abcde._3)
        bits4 <- D.encode(abcde._4)
        bits5 <- E.encode(abcde._5)
      } yield bits ++ bits2 ++ bits3 ++ bits4 ++ bits5

    def pxmap[X](to: (A,B,C,D,E) => X, from: X => Option[(A,B,C,D,E)]): Codec[X] = this.pxmap(to.tupled,from)

  }

  class Tuple6Codec[A,B,C,D,E,F](A: Codec[A], B: Codec[B], C: Codec[C], D: Codec[D], E: Codec[E], F: Codec[F]) extends Codec[(A,B,C,D,E,F)] {
    def ~~[G](G: Codec[G]): Tuple7Codec[A,B,C,D,E,F,G] = new Tuple7Codec(A,B,C,D,E,F,G)

    override def decode(bits: BitVector): Err \/ (BitVector, (A,B,C,D,E,F)) = {
      val x = for {
        aa <- A.decode(bits)
                      (bits1,a) = aa
        bb <- B.decode(bits1)
                      (bits2,b) = bb
        cc <- C.decode(bits2)
                      (bits3,c) = cc
        dd <- D.decode(bits3)
                      (bits4,d) = dd
        ee <- E.decode(bits4)
                      (bits5,e) = ee
        ff <- F.decode(bits5)
      } yield (ff._1, (a,b,c,d,e,ff._2))
      x.leftMap(e => Err(s"tuple3 from ${bits.size} bits -- " + e.messageWithContext))
    }

    override def encode(abcdef: (A,B,C,D,E,F)): Err \/ BitVector =
      for {
        bits <- A.encode(abcdef._1)
        bits2 <- B.encode(abcdef._2)
        bits3 <- C.encode(abcdef._3)
        bits4 <- D.encode(abcdef._4)
        bits5 <- E.encode(abcdef._5)
        bits6 <- F.encode(abcdef._6)
      } yield bits ++ bits2 ++ bits3 ++ bits4 ++ bits5 ++ bits6


    def pxmap[X](to: (A,B,C,D,E,F) => X, from: X => Option[(A,B,C,D,E,F)]): Codec[X] = this.pxmap(to.tupled,from)
  }

  class Tuple7Codec[A,B,C,D,E,F,G](A: Codec[A], B: Codec[B], C: Codec[C], D: Codec[D], E: Codec[E], F: Codec[F], G: Codec[G]) extends Codec[(A,B,C,D,E,F,G)] {
    override def decode(bits: BitVector): Err \/ (BitVector, (A,B,C,D,E,F,G)) = {
      val x = for {
        aa <- A.decode(bits)
                      (bits1,a) = aa
        bb <- B.decode(bits1)
                      (bits2,b) = bb
        cc <- C.decode(bits2)
                      (bits3,c) = cc
        dd <- D.decode(bits3)
                      (bits4,d) = dd
        ee <- E.decode(bits4)
                      (bits5,e) = ee
        ff <- F.decode(bits5)
                      (bits6,f) = ff
        gg <- G.decode(bits6)
      } yield (gg._1, (a,b,c,d,e,f,gg._2))
      x.leftMap(e => Err(s"tuple3 from ${bits.size} bits -- " + e.messageWithContext))
    }

    override def encode(abcdefg: (A,B,C,D,E,F,G)): scodec.Err \/ BitVector =
      for {
        bits <- A.encode(abcdefg._1)
        bits2 <- B.encode(abcdefg._2)
        bits3 <- C.encode(abcdefg._3)
        bits4 <- D.encode(abcdefg._4)
        bits5 <- E.encode(abcdefg._5)
        bits6 <- F.encode(abcdefg._6)
        bits7 <- G.encode(abcdefg._7)
      } yield bits ++ bits2 ++ bits3 ++ bits4 ++ bits5 ++ bits6 ++ bits7

    def pxmap[X](to: (A,B,C,D,E,F,G) => X, from: X => Option[(A,B,C,D,E,F,G)]): Codec[X] = this.pxmap(to.tupled,from)
  }
}
