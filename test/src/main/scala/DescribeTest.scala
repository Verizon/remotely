package remotely
package test

import scodec.Codec

case class Foo(a: Int)
case class Bar(a: Int)

object DescribeTestOlderProtocol {
  implicit lazy val fooCodec: Codec[Foo] = codecs.int32.as[Foo]
  implicit lazy val barCodec: Codec[Bar] = codecs.int32.as[Bar]
  implicit lazy val sigCodec: Codec[List[Signature]] = codecs.list(Signature.signatureCodec)

  val definition = Protocol.empty
    .codec[Foo]
    .codec[Bar]
    .specify0[Foo]("foo")
    .specify1[Foo, Foo]("fooId")
    .specify1[Foo, Bar]("foobar")
}


/**
  * This is just like the older protocol, but it adds a new method
  */
object DescribeTestNewerProtocol {
  implicit lazy val fooCodec: Codec[Foo] = codecs.int32.as[Foo]
  implicit lazy val barCodec: Codec[Bar] = codecs.int32.as[Bar]
  implicit lazy val sigCodec: Codec[List[Signature]] = codecs.list(Signature.signatureCodec)

  val definition = Protocol.empty
    .codec[Foo]
    .codec[Bar]
    .specify0[Foo]("foo")
    .specify1[Foo, Foo]("fooId")
    .specify1[Foo, Bar]("foobar")
    .specify0[Bar]("bar")
}

