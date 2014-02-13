package srpc

import scala.reflect.runtime.universe.TypeTag
import scala.collection.concurrent.TrieMap
import scalaz.concurrent.Task
import scalaz.Monad
import scodec.Codec

trait Service {

  protected val registry: TrieMap[String,Unit] =
    new TrieMap()

  // polymorphic functions will have to be declared
  // differently

  protected def ref[A:TypeTag](s: String): Remote[A] = {
    val tag = Remote.nameToTag[A](s)
    val r = Remote.Ref[A](tag)
    registry += (tag -> ())
    r
  }

  def local[A:Codec:TypeTag](a: A): Remote[A] =
    Remote.Local(a, Codec[A], Remote.toTag[A])

  def async[A:Codec:TypeTag](a: Task[A]): Remote[A] =
    Remote.Async(a, Codec[A], Remote.toTag[A])

  val syntax: ServiceSyntax = ServiceSyntax(this)
}

trait FactorialService extends Service {
  def factorial: Remote[Int => Int] = ref("factorial")
}

// better idea - generate the server interface
// could write a little tool to generate the client using reflection
// trait Foo extends Service {
//   def factorial
// trait Server {
//   def lookup(name: String): Option[
// as part of each request, client should send signature
// of all functions it knows about
// server verifies it has a superset of these, otherwise
// fails fast

object Service {


}

object ServiceExample {

  import Remoteable._
  import Codecs._

  val c: Service = ???
  import c.syntax._
  val fac: Remote[Int => Int] = ???
  val gcd: Remote[(Int,Int) => Int] = ???
  val ar = fac(c.local(9))
  val ar1 = gcd(1, 2)
  val ar2: Remote[Int] = ar
}

case class ServiceSyntax(C: Service) {

  implicit class Ap1[A,B:Remoteable](self: Remote[A => B]) {
    def apply(a: Remote[A]): Remote[B] =
      Remote.Ap1(self, a)
  }

  implicit class Ap2[A,B,C:Remoteable](self: Remote[(A,B) => C]) {
    def apply(a: Remote[A], b: Remote[B]): Remote[C] =
      Remote.Ap2(self, a, b)
  }
}
