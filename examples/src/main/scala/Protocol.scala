package remotely
package examples
import scalaz._
import codecs._

// NB: The GenServer macro needs to receive the FQN of all types, or import them
// explicitly. The target of the macro needs to be an abstract class.
@GenServer(remotely.Protocol.empty.codec[Int].specify[Int => Int]("fac"))
  abstract class FacServer

// The `GenClient` macro needs to receive the FQN of all types, or import them
// explicitly. The target needs to be an object declaration.
@GenClient(remotely.Protocol.empty.codec[Int].specify[Int => Int]("fac").signatures)
  object FacClient

@GenServer(remotely.Protocol.empty.codec[List[Int]]) abstract class Foo

object TestProtocol {

  // We can make a server
  lazy val facServer = new FacServer {
    val fac = (n: Int) => Response.now { (1 to n).product }
  }

  // We can get the environment out of a generated server:
  lazy val env = facServer.environment
}

