package srpc

import scala.reflect.runtime.universe.TypeTag
import scodec.{Codec,Decoder,Encoder}

case class Protocol(codecs: Codecs, signatures: Signatures) {

  def decoders = codecs.decoders
  def encoders = codecs.encoders

  def encoder[A:TypeTag:Encoder]: Protocol =
    this.copy(codecs = codecs.encoder[A])

  def decoder[A:TypeTag:Decoder]: Protocol =
    this.copy(codecs = codecs.decoder[A])

  def codec[A:TypeTag:Codec]: Protocol =
    this.copy(codecs = codecs.codec[A])

  def specify[A:TypeTag](name: String): Protocol =
    this.copy(signatures = signatures.specify[A](name))

  def specify1[A:TypeTag,B:TypeTag](name: String): Protocol =
    specify[A => B](name)

  def specify2[A:TypeTag,B:TypeTag,C:TypeTag](name: String): Protocol =
    specify[(A,B) => C](name)

  def specify3[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag](name: String): Protocol =
    specify[(A,B,C) => D](name)

  def specify4[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag](name: String): Protocol =
    specify[(A,B,C,D) => E](name)

  def generateClient(moduleName: String): String =
    signatures.generateClient(moduleName)

  def generateServer(traitName: String): String = s"""
  |import srpc.{Codecs,Decoders,Encoders,Environment,Values}
  |
  |trait $traitName {
  |  // This interface is generated from a `Protocol`. Do not modify.
  |  def environment: Environment = Environment(
  |${Signatures.indent("    ")(codecs.pretty)},
  |    populateDeclarations(Values.empty)
  |  )
  |
  |${Signatures.indent("  ")(signatures.generateServerTraitBody)}
  |}
  """.stripMargin

  def pretty: String =
    "Protocol(\n" +
    Signatures.indent("  ")(codecs.pretty) + ",\n" +
    Signatures.indent("  ")(signatures.pretty) + "\n)"

  override def toString = pretty
}

object Protocol {

  val empty = Protocol(Codecs.empty, Signatures.empty)
}
