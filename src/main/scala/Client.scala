package srpc

import scalaz.concurrent.Task
import scalaz.Monad
import scodec.Codec

trait Client[F[_]] {

  def eval[A](a: Remote[A]): F[A]
  def run[A](f: F[A]): A

  def force[A](a: Remote[A]): A = run { eval(a) }
  def local[A:Codec:ClassManifest](a: A): Remote[A] =
    Remote.Local(a, Codec[A], implicitly[ClassManifest[A]])
  def async[A:Codec:ClassManifest](a: Task[A]): Remote[A] =
    Remote.Async(a, Codec[A], implicitly[ClassManifest[A]])

  val syntax: ClientSyntax[F] = ClientSyntax(this)
}

object Client {
  def local(env: PartialFunction[String, Any]): Client[Task] = new Client[Task] {
    import Remote._
    val T = Monad[Task]

    def run[A](t: Task[A]): A = t.run

    def eval[A](r: Remote[A]): Task[A] = r match {
      case Async(a, _, _) => a
      case Local(a,_,_) => Task.now(a)
      case Ref(name) => env.lift(name) match {
        case None => Task.delay { sys.error("Unknown name on server: " + name) }
        case Some(a) => Task.now(a.asInstanceOf[A])
      }
      case Ap1(f,a) => T.apply2(eval(f), eval(a))(_(_))
      case Ap2(f,a,b) => T.apply3(eval(f), eval(a), eval(b))(_(_,_))
      case Ap3(f,a,b,c) => T.apply4(eval(f), eval(a), eval(b), eval(c))(_(_,_,_))
      case Ap4(f,a,b,c,d) => T.apply5(eval(f), eval(a), eval(b), eval(c), eval(d))(_(_,_,_,_))
    }
  }
}

object ClientExample {

  import Remoteable._
  import Codecs._

  val c: Client[Task] = ???
  import c.syntax._
  val fac: Remote[Int => Int] = ???
  val gcd: Remote[(Int,Int) => Int] = ???
  val ar = fac(c.local(9))
  val ar1 = gcd(1, 2)
  val ar2: Remote[Int] = ar
}

case class ClientSyntax[F[_]](C: Client[F]) {

  implicit class Ap1[A,B:Remoteable](self: Remote[A => B]) {
    def apply(a: Remote[A]): Remote[B] =
      Remote.Ap1(self, a)
  }

  implicit class Ap2[A,B,C:Remoteable](self: Remote[(A,B) => C]) {
    def apply(a: Remote[A], b: Remote[B]): Remote[C] =
      Remote.Ap2(self, a, b)
  }
}
