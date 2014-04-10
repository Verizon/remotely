package remotely

import java.util.UUID
import scala.util.{Success,Failure}
import scalaz.{Catchable,Monad}
import scalaz.concurrent.Task
import scalaz.\/
import scalaz.\/.{left,right}

import Response.Context

/** The result type for a remote computation. */
sealed trait Response[+A] {
  def apply(c: Context): Task[A]

  def flatMap[B](f: A => Response[B]): Response[B] =
    Response { ctx => Task.suspend { this(ctx).flatMap(f andThen (_(ctx))) }}

  def map[B](f: A => B): Response[B] =
    Response { ctx => Task.suspend { this(ctx) map f }}

  def attempt: Response[Throwable \/ A] =
    Response { ctx => Task.suspend { this(ctx).attempt }}
}

object Response {

  /** Create a `Response[A]` from a `Context => Task[A]`. */
  def apply[A](f: Context => Task[A]): Response[A] = new Response[A] {
    def apply(c: Context): Task[A] = f(c)
  }

  /** Monad instance for `Response`. */
  implicit val responseInstance = new Monad[Response] with Catchable[Response] {
    def point[A](a: => A): Response[A] = {
      lazy val result = a // memoized - `point` should not be used for side effects
      Response(_ => Task.now(a))
    }
    def bind[A,B](a: Response[A])(f: A => Response[B]): Response[B] =
      Response { ctx => Task.suspend { a(ctx).flatMap(f andThen (_(ctx))) }}
    def attempt[A](a: Response[A]): Response[Throwable \/ A] = a.attempt
    def fail[A](err: Throwable): Response[A] = Response.fail(err)
  }

  /** Fail with the given `Throwable`. */
  def fail(err: Throwable): Response[Nothing] = Response { _ => Task.fail(err) }

  /** Produce a `Response[A]` from a strict value. */
  def now[A](a: A): Response[A] = Response { _ => Task.now(a) }

  /**
   * Produce a `Response[A]` from a nonstrict value, whose result
   * will not be cached if this `Response` is used more than once.
   */
  def delay[A](a: => A): Response[A] = Response { _ => Task.delay(a) }

  /** Produce a `Response` nonstrictly. Do not cache the produced `Response`. */
  def suspend[A](a: => Response[A]): Response[A] =
    Response { ctx => Task.suspend { a(ctx) } }

  /** Obtain the current `Context`. */
  def ask: Response[Context] = Response { ctx => Task.now(ctx) }

  /** Obtain a portion of the current `Context`. */
  def asks[A](f: Context => A): Response[A] = Response { ctx => Task.delay(f(ctx)) }

  /** Apply the given function to the `Context` before passing it to `a`. */
  def local[A](f: Context => Context)(a: Response[A]): Response[A] =
    Response { ctx => Task.suspend { a(f(ctx)) }}

  /** Apply the given effectful function to the `Context` before passing it to `a`. */
  def localF[A](f: Response[Context])(a: Response[A]): Response[A] =
    f flatMap { ctx => local(_ => ctx)(a) }

  /** Push a fresh `ID` onto the tracing stack before invoking `a`. */
  def scope[A](a: Response[A]): Response[A] =
    localF(fresh.flatMap(id => ask.map(_ push id)))(a)

  /**
   * Create a response by registering a completion callback with the given
   * asynchronous function, `register`.
   */
  def async[A](register: ((Throwable \/ A) => Unit) => Unit): Response[A] =
    Response { _ => Task.async { register }}

  /** Create a `Response[A]` from a `Task[A]`. */
  def async[A](a: Task[A]): Response[A] = Response { _ => a }

  /** Alias for [[remotely.Response.fromFuture]]. */
  def async[A](f: scala.concurrent.Future[A])(implicit E: scala.concurrent.ExecutionContext): Response[A] =
    fromFuture(f)

  /**
   * Create a response from a `Future`. Note that since `Future` caches its
   * result, it is not safe to reuse this `Response` to repeat the same
   * computation.
   */
  def fromFuture[A](f: scala.concurrent.Future[A])(implicit E: scala.concurrent.ExecutionContext): Response[A] =
    async { cb => f.onComplete {
      case Success(a) => cb(right(a))
      case Failure(e) => cb(left(e))
    }}

  /**
   * Opaque identifier used for tracking requests.
   * Only public API is `hashCode`, `equals`, `toString`.
   */
  sealed trait ID { // sealed, therefore we only have to update this file in the event we change its representation
    private[remotely] def get: UUID
    override def hashCode = get.hashCode
    override def toString = get.toString
    override def equals(a: Any) = a match {
      case id: ID => id.get == get
      case _ => false
    }
  }

  object ID {
    private[remotely] def fromString(s: String): ID =
      new ID { val get = UUID.fromString(s) } // let the exception propagate
  }

  /** Create a new `ID`, guaranteed to be globally unique. */
  def fresh: Response[ID] = async { Task.delay { new ID { val get = UUID.randomUUID } } }

  /**
   * An environment used when generating a response.
   * The `header` may be used for dynamically typed configuration
   * and/or metadata, and the `ID` stack is useful for tracing.
   * See the [[remotely.Response.scope]] combinator.
   */
  case class Context(header: Map[String,String],
                     stack: List[ID]) {

    /** Push the given `ID` onto the `stack` of this `Context`. */
    def push(id: ID): Context = copy(stack = id :: stack)

    /** Add the given entries to the `header` of this `Context`. */
    def entries(kvs: (String,String)*): Context = copy(header = header ++ kvs)
  }

  object Context {

    /** The empty `Context`, contains an empty header and tracing stack. */
    val empty = Context(Map(), List())
  }
}
