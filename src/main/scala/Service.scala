package srpc

import java.net.InetSocketAddress
import scalaz.concurrent.Task

object ServiceExample extends App {

  import Codecs._
  import Remoteable._

  def foo(i: Int): String = ???

  // on server, populate environment with codecs and values
  val env = Environment.empty
    .codec[Int]
    .codec[String]
    .codec[Double]
    .codec[Float]
    .codec[List[Double]]
    .codec[List[String]]
    .declare("sum") { (d: List[Double]) => d.sum }
    .declare("fac") { (n: Int) => (1 to n).foldLeft(1)(_ * _) }
    .declare("foo") { foo _ } // referencing existing functions works, too

  val addr = new InetSocketAddress("localhost", 8080)

  // create a server for this environment
  val server = Server.start(env)(addr).run.runAsync { _ => () }

  // on client - create local, typed declarations for server
  // functions you wish to call. This can be code generated
  // from `env`, since `env` has name/types for all declarations!

  val fac: Remote[Int => Int] = Remote.ref("fac")
  val gcd: Remote[(Int,Int) => Int] = Remote.ref("gcd")
  val sum: Remote[List[Double] => Double] = Remote.ref("sum")

  // And actual client code uses normal looking function calls
  val ar = fac(9)
  val ar1 = gcd(1, 2)
  val ar3 = sum(List(0.0, 1.1, 2.2))
  val ar2: Remote[Int] = ar
  val r: Remote[Double] = ar3

  // to actually run a remote expression, we need an endpoint
  val loc: Endpoint = Endpoint.single(addr)
  val result: Task[Double] = eval(loc)(r)
  println { result.run }
}
