package srpc

import scodec.{Codec,codecs => C}

object Codecs {

  implicit val int32 = C.int32
  implicit val int64 = C.int64
  implicit val utf8 = C.utf8
  implicit val bool = C.bool

  implicit def tuple2[A:Codec,B:Codec]: Codec[(A,B)] =
    Codec[A] ~ Codec[B]

  implicit def byteArray: Codec[Array[Byte]] = ???

  implicit def seq[A:Codec]: Codec[Seq[A]] =
    C.repeated(Codec[A]).xmap[Seq[A]](
      a => a,
      _.toIndexedSeq
    )

  implicit def indexedSeq[A:Codec]: Codec[collection.immutable.IndexedSeq[A]] =
    C.repeated(Codec[A])

  implicit def list[A:Codec]: Codec[List[A]] =
    C.repeated(Codec[A]).xmap[List[A]](
      _.toList,
      _.toIndexedSeq)
}
