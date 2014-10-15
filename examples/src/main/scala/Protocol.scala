package remotely
package examples
import scalaz._

// NB: The GenServer macro needs to receive the FQN of all types, or import them
// explicitly.
// The target of the macro needs to be an abstract class.
@GenServer(remotely.Protocol.empty.codec[Boolean].specify[Boolean]("foo")) abstract class Foo

class Baz extends Foo {
  lazy val foo: Response[Boolean] = Monad[Response].point(true)
}

@GenServer({
  import remotely._
  Protocol.empty.codec[Int].specify[Int]("bar")
}) abstract class Bar

class Qux extends Bar {
  def bar: Response[Int] = Monad[Response].point(10)
}

object TestProtocol {
  // We can get the environment out of a generated protocol:
  val foo = new Baz
  val env = foo.environment
}

