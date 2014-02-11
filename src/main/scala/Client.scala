package srpc

import scalaz.concurrent.Task
import scalaz.Monad
import scodec.Codec

trait Client {

  def local[A:Codec:ClassManifest](a: A): Remote[A] =
    Remote.Local(a, Codec[A], implicitly[ClassManifest[A]])

  def async[A:Codec:ClassManifest](a: Task[A]): Remote[A] =
    Remote.Async(a, Codec[A], implicitly[ClassManifest[A]])

  val syntax: ClientSyntax = ClientSyntax(this)
}

trait FactorialClient extends Client {
  def factorial: Remote[Int => Int] = Remote.Ref("factorial")
}

// could write a little tool to generate the client using reflection

object Client {
  def local[A](env: PartialFunction[String, Any])(r: Remote[A]): Task[A] = {
    import Remote._
    val T = Monad[Task]
    r match {
      case Async(a, _, _) => a
      case Local(a,_,_) => Task.now(a)
      case Ref(name) => env.lift(name) match {
        case None => Task.delay { sys.error("Unknown name on server: " + name) }
        case Some(a) => Task.now(a.asInstanceOf[A])
      }
      case Ap1(f,a) => T.apply2(local(env)(f), local(env)(a))(_(_))
      case Ap2(f,a,b) => T.apply3(local(env)(f), local(env)(a), local(env)(b))(_(_,_))
      case Ap3(f,a,b,c) => T.apply4(local(env)(f), local(env)(a), local(env)(b), local(env)(c))(_(_,_,_))
      case Ap4(f,a,b,c,d) => T.apply5(local(env)(f), local(env)(a), local(env)(b), local(env)(c), local(env)(d))(_(_,_,_,_))
    }
  }


}

object ClientExample {

  import Remoteable._
  import Codecs._

  val c: Client = ???
  import c.syntax._
  val fac: Remote[Int => Int] = ???
  val gcd: Remote[(Int,Int) => Int] = ???
  val ar = fac(c.local(9))
  val ar1 = gcd(1, 2)
  val ar2: Remote[Int] = ar
}

case class ClientSyntax(C: Client) {

  implicit class Ap1[A,B:Remoteable](self: Remote[A => B]) {
    def apply(a: Remote[A]): Remote[B] =
      Remote.Ap1(self, a)
  }

  implicit class Ap2[A,B,C:Remoteable](self: Remote[(A,B) => C]) {
    def apply(a: Remote[A], b: Remote[B]): Remote[C] =
      Remote.Ap2(self, a, b)
  }
}
