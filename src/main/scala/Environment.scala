package remotely

import scala.reflect.runtime.universe.TypeTag
import scodec.{Codec,Decoder,Encoder}

// could do dynamic lookup of encoder, decoder, using type tags

case class Environment(codecs: Codecs, values: Values) {

  def decoders = codecs.decoders
  def encoders = codecs.encoders

  def encoder[A:TypeTag:Encoder]: Environment =
    this.copy(codecs = codecs.encoder[A])

  def decoder[A:TypeTag:Decoder]: Environment =
    this.copy(codecs = codecs.decoder[A])

  def codec[A](implicit T: TypeTag[A], C: Codec[A]): Environment =
    this.copy(codecs = codecs.codec[A])

  def codecs(c: Codecs): Environment = Environment(codecs ++ c, values)

  /** Declare or update the value for the given name in this `Environment` */
  def update[A:TypeTag](name: String)(a: A): Environment =
    this.copy(values = values.update[A](name)(a))

  /**
   * Declare the value for the given name in this `Environment`,
   * or throw an error if the type-qualified name is already bound.
   */
  def declare[A:TypeTag](name: String)(a: A): Environment =
    this.copy(values = values.declare[A](name)(a))

  /**
   * Convenience function which just calls `declare[A => B](name)`.
   */
  def declare1[A:TypeTag,B:TypeTag](name: String)(f: A => B): Environment =
    declare[A => B](name)(f)

  /**
   * Convenience function which just calls `declare[(A,B) => C](name)`.
   */
  def declare2[A:TypeTag,B:TypeTag,C:TypeTag](name: String)(f: (A,B) => C): Environment =
    declare[(A,B) => C](name)(f)

  /**
   * Convenience function which just calls `declare[(A,B,C) => D](name)`.
   */
  def declare3[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag](name: String)(f: (A,B,C) => D): Environment =
    declare[(A,B,C) => D](name)(f)

  /**
   * Convenience function which just calls `declare[(A,B,C,D) => E](name)`.
   */
  def declare4[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag](name: String)(f: (A,B,C,D) => E): Environment =
    declare[(A,B,C,D) => E](name)(f)

  /**
   * Serve this `Environment` via a TCP server at the given address.
   * Returns a thunk that can be used to stop the server.
   */
  def serve(addr: java.net.InetSocketAddress): () => Unit =
    Server.start(this)(addr)

  /** Generate the Scala code for the client access to this `Environment`. */
  def generateClient(moduleName: String): String =
    Signatures(values.keySet).generateClient(moduleName)

  override def toString = {
    s"""Environment {
    |  ${values.keySet.toList.sorted.mkString("\n  ")}
    |
    |  decoders:
    |    ${decoders.keySet.toList.sorted.mkString("\n    ")}
    |
    |  encoders:
    |    ${encoders.keySet.toList.sorted.mkString("\n    ")}
    |}
    """.stripMargin
  }
}

object Environment {
  val empty = Environment(Codecs.empty, Values.empty)
}
