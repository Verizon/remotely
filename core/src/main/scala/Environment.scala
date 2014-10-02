package remotely

import java.net.InetSocketAddress
import javax.net.ssl.SSLEngine
import scala.reflect.runtime.universe.TypeTag
import scodec.{Codec,Decoder,Encoder}
import scodec.bits.{ByteVector}
import scalaz.stream.Process

/**
 * A collection of codecs and values, which can be populated
 * and then served over RPC.
 *
 * Example: {{{
 *   val env: Environment = Environment.empty
 *     .codec[Int]
 *     .codec[List[Int]]
 *     .populate { _
 *        .declareStrict[List[Int] => Int]("sum", _.sum)
 *        .declare("fac", (n: Int) => Task { (1 to n).product })
 *     }
 *   val stopper = env.serve(new InetSocketAddress("localhost",8080))
 *   ///
 *   stopper() // shutdown the server
 * }}}
 */
case class Environment(codecs: Codecs, values: Values) {

  def decoders = codecs.decoders
  def encoders = codecs.encoders

  def encoder[A:TypeTag:Encoder]: Environment =
    this.copy(codecs = codecs.encoder[A])

  def decoder[A:TypeTag:Decoder]: Environment =
    this.copy(codecs = codecs.decoder[A])

  def codec[A](implicit T: TypeTag[A], C: Codec[A]): Environment =
    this.copy(codecs = codecs.codec[A])

  /** Add the given codecs to this `Environment`, keeping existing codecs. */
  def codecs(c: Codecs): Environment =
    Environment(codecs ++ c, values)

  /**
   * Modify the values inside this `Environment`, using the given function `f`.
   * Example: `Environment.empty.populate { _.declare("x")(Task.now(42)) }`.
   */
  def populate(f: Values => Values): Environment =
    this.copy(values = f(values))

  /** Alias for `this.populate(_ => v)`. */
  def values(v: Values): Environment =
    this.populate(_ => v)

  private def serverHandler(monitoring: Monitoring): server.Handler =
    server.Handler { bytes =>
      // we assume the input is a framed stream, and encode the response(s)
      // as a framed stream as well
      bytes pipe Process.await1[ByteVector] /*server.Handler.deframe*/ evalMap { bs =>
        Server.handle(this)(bs.toBitVector)(monitoring).map(_.toByteVector)
      } /*pipe server.Handler.enframe*/
    }

  /** Start an RPC server on the given port. */
  def serve(addr: InetSocketAddress)(monitoring: Monitoring = Monitoring.empty): () => Unit =
    server.start("rpc-server")(serverHandler(monitoring), addr, None)

  /** Start an RPC server on the given port using an `SSLEngine` provider. */
  def serveSSL(addr: InetSocketAddress, ssl: () => SSLEngine)(
      monitoring: Monitoring = Monitoring.empty): () => Unit =
    server.start("ssl-rpc-server")(serverHandler(monitoring), addr, Some(ssl))

  /** Generate the Scala code for the client access to this `Environment`. */
  def generateClient(moduleName: String, pkg: String): String =
    Signatures(values.keySet).generateClient(moduleName, pkg)

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
