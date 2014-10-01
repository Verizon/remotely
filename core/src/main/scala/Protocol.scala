package remotely

import scala.reflect.runtime.universe.TypeTag
import scodec.{Codec,Decoder,Encoder}

case class Protocol(codecs: Codecs, signatures: Signatures) {

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

  def generateClient(moduleName: String, pkg: String = "default"): String =
    signatures.generateClient(moduleName, pkg)

  def generateServer(traitName: String, pkg: String = "default"): String = s"""
  |package $pkg
  |
  |import remotely.{Codecs,Environment,Response,Values}
  |import remotely.codecs._
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
