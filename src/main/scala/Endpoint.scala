package srpc

import java.net.{InetSocketAddress,URL}
import javax.net.ssl.SSLParameters
import scalaz.concurrent.Task
import scalaz.stream.{async,Bytes,Channel,Exchange,Process,nio}
import scodec.bits.{BitVector,ByteVector}
import Endpoint.Connection

/**
 * A 'logical' endpoint for some service, represented
 * by a possibly rotating stream of `Connection`s.
 */
case class Endpoint(connections: Process[Task,Connection]) {
  def get: Task[Connection] = connections.once.runLast.flatMap {
    case None => Task.fail(new Exception("No available connections"))
    case Some(a) => Task.now(a)
  }
}

object Endpoint {

  type Connection = Process[Task,ByteVector] => Process[Task,ByteVector]

  // combinators for building up Endpoints

  def empty: Endpoint = Endpoint(Process.halt)

  def single(host: InetSocketAddress): Endpoint =
    Endpoint(Process.constant(request(host)))

  /**
   * Send a stream of bytes to a server and get back a
   * stream of bytes, allowing for nondeterminism in the
   * rate of processing. (That is, we never block on awaiting
   * confirmation of sending bytes to the server.) Note that
   * this does not do an SSL handshake or encryption.
   */
  private def request(host: InetSocketAddress)(
                      bytes: Process[Task,ByteVector]): Process[Task,ByteVector] =
    nio.connect(host).flatMap { exch =>
      val w = (bytes.map(chunk => Bytes.of(chunk.toArray)) to exch.write).drain
      val r = exch.read.map(bs => ByteVector(bs.toArray))
      r.merge(w)
    }

  def singleSSL(ssl: SSLParameters)(c: InetSocketAddress): Endpoint = ???

  def roundRobin(p: Endpoint*): Endpoint = {
    require(p.nonEmpty, "round robin endpoint must have at least one endpoint to choose from")
    val pts = p.toIndexedSeq
    reduceBalanced(pts)(_ => 1)((a,b) => Endpoint(a.connections.interleave(b.connections)))
  }

  private def reduceBalanced[A](v: TraversableOnce[A])(size: A => Int)(
                      f: (A,A) => A): A = {
    @annotation.tailrec
    def fixup(stack: List[(A,Int)]): List[(A,Int)] = stack match {
      // h actually appeared first in `v`, followed by `h2`, preserve this order
      case (h2,n) :: (h,m) :: t if n > m/2 =>
        fixup { (f(h, h2), m+n) :: t }
      case _ => stack
    }
    v.foldLeft(List[(A,Int)]())((stack,a) => fixup((a -> size(a)) :: stack))
     .reverse.map(_._1)
     .reduceLeft(f)
  }

  def logical(name: String)(resolve: String => Endpoint): Endpoint =
    resolve(name)

  // loadBalanced
  // circuitBroken
  // when a connection fails, it is removed from the pool
  // until the next tick of schedule; when pool is empty
  // we fail fast
  // def circuitBroken(schedule: Process[Task,Unit])(
  // healthy: Channel[Task, InetSocketAddress, Boolean])(
  // addr: Endpoint): Endpoint = ???
}
