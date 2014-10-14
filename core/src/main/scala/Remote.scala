package remotely

import scala.collection.immutable.SortedSet
import scala.reflect.runtime.universe._
import scalaz.concurrent.Task
import scalaz.{\/, Applicative, Monad, Nondeterminism}
import scala.reflect.runtime.universe.TypeTag
import scodec.{Codec,Decoder,Encoder}
import scodec.bits.BitVector
import shapeless._

/**
 * Represents a remote computation which yields a
 * value of type `A`. Remote expressions can be serialized
 * and sent to a server for evaluation.
 */
sealed trait Remote[+A] {

  override def toString = pretty

  def pretty: String = "Remote {\n  " +
    Remote.refs(this).mkString("\n  ") + "\n  " +
    toString + "\n}"
}

object Remote {

  /** Reference a remote value on the server, assuming it has the given type. */
  def ref[A:TypeTag](s: String): Remote[A] = {
    val tag = Remote.nameToTag[A](s)
    Remote.Ref[A](tag)
  }

  /** Promote a local value to a remote value. */
  def local[A:Encoder:TypeTag](a: A): Remote[A] =
    Remote.Local(a, Some(Encoder[A]), Remote.toTag[A])

  /** Promote an asynchronous `Task` to a remote value. */
  def async[A:Encoder:TypeTag](a: Task[A]): Remote[A] =
    Remote.Async(Response.async(a), Encoder[A], Remote.toTag[A])

  /** Promote a `Response` (from another request) to a remote value. */
  def response[A:Encoder:TypeTag](a: Response[A]): Remote[A] =
    Remote.Async(a, Encoder[A], Remote.toTag[A])

  /** Provides the syntax `expr.run(endpoint)`, where `endpoint: Endpoint`. */
  implicit class RunSyntax[A](self: Remote[A]) {

    /**
     * Run this `Remote[A]` at the given `Endpoint`. We require a `TypeTag[A]` and
     * `Codec[A]` in order to deserialize the response and check that it has the expected type.
     */
    def run(at: Endpoint, M: Monitoring = Monitoring.empty)(implicit A: TypeTag[A], C: Codec[A]): Response[A] =
      evaluate(at, M)(self)

    /** Call `self.run(at, M).apply(ctx)` to get back a `Task[A]`. */
    def runWithContext(at: Endpoint, ctx: Response.Context, M: Monitoring = Monitoring.empty)(implicit A: TypeTag[A], C: Codec[A]): Task[A] =
      run(at, M).apply(ctx)
  }
  implicit class Ap1Syntax[A,B](self: Remote[A => B]) {
    def apply(a: Remote[A]): Remote[B] =
      Remote.Ap1(self, a)
  }
  implicit class Ap2Syntax[A,B,C](self: Remote[(A,B) => C]) {
    def apply(a: Remote[A], b: Remote[B]): Remote[C] =
      Remote.Ap2(self, a, b)
  }
  implicit class Ap3Syntax[A,B,C,D](self: Remote[(A,B,C) => D]) {
    def apply(a: Remote[A], b: Remote[B], c: Remote[C]): Remote[D] =
      Remote.Ap3(self, a, b, c)
  }
  implicit class Ap4Syntax[A,B,C,D,E](self: Remote[(A,B,C,D) => E]) {
    def apply(a: Remote[A], b: Remote[B], c: Remote[C], d: Remote[D]): Remote[E] =
      Remote.Ap4(self, a, b, c, d)
  }

  /** Promote a local value to a remote value. */
  private[remotely] case class Local[A](
    a: A, // the value
    format: Option[Encoder[A]], // serializer for `A`
    tag: String // identifies the deserializer to be used by server
  ) extends Remote[A] {
    override def toString = a.toString
  }


  /** Promote an asynchronous task to a remote value. */
  private[remotely] case class Async[A](
    a: Response[A],
    format: Encoder[A], // serializer for `A`
    tag: String // identifies the deserializer to be used by server
  ) extends Remote[A] {
    override def toString = s"Async[$tag]"
  }

  /**
   * Reference to a remote value on the server.
   */
  private[remotely] case class Ref[A](name: String) extends Remote[A] {
    override def toString = name.takeWhile(_ != ':')
  }

  // we require a separate constructor for each function
  // arity, since remote invocations must be fully saturated
  private[remotely] case class Ap1[A,B](
    f: Remote[A => B],
    a: Remote[A]) extends Remote[B] {
    override def toString = s"$f($a)"
  }

  private[remotely] case class Ap2[A,B,C](
    f: Remote[(A,B) => C],
    a: Remote[A],
    b: Remote[B]) extends Remote[C] {
    override def toString = s"$f($a, $b)"
  }

  private[remotely] case class Ap3[A,B,C,D](
    f: Remote[(A,B,C) => D],
    a: Remote[A],
    b: Remote[B],
    c: Remote[C]) extends Remote[D] {
    override def toString = s"$f($a, $b, $c)"
  }

  private[remotely] case class Ap4[A,B,C,D,E](
    f: Remote[(A,B,C,D) => E],
    a: Remote[A],
    b: Remote[B],
    c: Remote[C],
    d: Remote[D]) extends Remote[E] {
    override def toString = s"$f($a, $b, $c, $d)"
  }

  /**
   * Precursor to serializing a remote computation
   * to send to server for evaluation. This function
   * removes all `Async` constructors by executing all
   * outstanding `Async` in parallel.
   */
  def localize[A](r: Remote[A]): Response[Remote[A]] = r match {
    // NB: change this to just `Monad[Response].apply2(..)` if want to issue requests sequentially
    case Async(a,c,t) => a.map { a => Local(a,Some(c).asInstanceOf[Option[Encoder[A]]],t) }
    case Ap1(f,a) => Response.par.apply2(localize(f), localize(a))(Ap1.apply)
    case Ap2(f,a,b) => Response.par.apply3(localize(f), localize(a), localize(b))(Ap2.apply)
    case Ap3(f,a,b,c) => Response.par.apply4(localize(f), localize(a), localize(b), localize(c))(Ap3.apply)
    case Ap4(f,a,b,c,d) => Response.par.apply5(localize(f), localize(a), localize(b), localize(c), localize(d))(Ap4.apply)
    case _ => Response.now(r) // Ref or Local
  }

  /** Collect up all the `Ref` names referenced by `r`. */
  def refs[A](r: Remote[A]): SortedSet[String] = r match {
    case Local(a,e,t) => SortedSet.empty
    case Async(a,e,t) => sys.error(
      "cannot encode Async constructor; call Remote.localize first")
    case Ref(t) => SortedSet(t)
    case Ap1(f,a) => refs(f).union(refs(a))
    case Ap2(f,a,b) => refs(f).union(refs(b)).union(refs(b))
    case Ap3(f,a,b,c) => refs(f).union(refs(b)).union(refs(b)).union(refs(c))
    case Ap4(f,a,b,c,d) => refs(f).union(refs(b)).union(refs(b)).union(refs(c)).union(refs(d))
  }

  /** Collect up all the formats referenced by `r`. */
  def formats[A](r: Remote[A]): SortedSet[String] = r match {
    case Local(a,e,t) => SortedSet(t)
    case Async(a,e,t) => sys.error(
      "cannot encode Async constructor; call Remote.localize first")
    case Ref(t) => SortedSet.empty
    case Ap1(f,a) => formats(f).union(formats(a))
    case Ap2(f,a,b) => formats(f).union(formats(b)).union(formats(b))
    case Ap3(f,a,b,c) => formats(f).union(formats(b)).union(formats(b)).union(formats(c))
    case Ap4(f,a,b,c,d) => formats(f).union(formats(b)).union(formats(b)).union(formats(c)).union(formats(d))
  }

  def toTag[A:TypeTag]: String = {
    val tt = typeTag[A]
    val result = tt.tpe.toString
    if(result.startsWith("java.lang.")) result.drop(10)
    else if (result.startsWith("scala.")) result.drop(6)
    else result
  }

  def nameToTag[A:TypeTag](s: String): String =
    s"$s: ${toTag[A]}"

  /** Lower priority implicits. */
  private[remotely] trait lowpriority {
    implicit def codecIsRemote[A:Codec:TypeTag](a: A): Remote[A] = local(a)
  }

  /** Provides implicits for promoting values to `Remote[A]`. */
  object implicits extends lowpriority {

    /** Implicitly promote a `Task[A]` to a `Remote[A]`. */
    implicit def responseToRemote[A:Encoder:TypeTag](t: Response[A]): Remote[A] = response(t)

    /** Implicitly promote a `Task[A]` to a `Remote[A]`. */
    implicit def taskToRemote[A:Encoder:TypeTag](t: Task[A]): Remote[A] = async(t)

    /** Implicitly promote a `Task[A]` to a `Remote[A]`. */
    implicit def localToRemote[A:Encoder:TypeTag](a: A): Remote[A] = local(a)
  }
}

