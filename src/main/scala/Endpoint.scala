package srpc

import java.net.{InetSocketAddress,URL}
import javax.net.ssl.SSLParameters
import scalaz.concurrent.Task
import scalaz.stream.{Bytes,Channel,Process,Exchange}
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

  type Connection = Process[Task,Bytes] => Process[Task,Bytes]

  // combinators for building up Endpoints

  def single(c: InetSocketAddress): Endpoint = ???

  def singleSSL(ssl: SSLParameters)(c: InetSocketAddress): Endpoint = ???

  def roundRobin(c: Endpoint*): Endpoint =
    ??? // just interleave all the streams

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
