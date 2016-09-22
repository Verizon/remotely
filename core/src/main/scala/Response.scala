//: ----------------------------------------------------------------------------
//: Copyright (C) 2014 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------

package remotely

import java.util.UUID
import scala.util.{Success,Failure}
import scalaz.{Applicative,Catchable,Functor,Monad,Nondeterminism}
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

  /** Modify the asynchronous result of this `Response`. */
  def edit[B](f: Task[A] => Task[B]): Response[B] =
    Response { ctx => Task.suspend { f(this.apply(ctx))} }
}

object Response {

  /** Create a `Response[A]` from a `Context => Task[A]`. */
  def apply[A](f: Context => Task[A]): Response[A] = new Response[A] {
    def apply(c: Context): Task[A] = f(c)
  }

  /** Monad instance for `Response`. */
  implicit val responseInstance = new Monad[Response] with Catchable[Response] with Nondeterminism[Response] {
    def point[A](a: => A): Response[A] = {
      lazy val result = a // memoized - `point` should not be used for side effects
      Response(_ => Task.now(a))
    }
    def bind[A,B](a: Response[A])(f: A => Response[B]): Response[B] =
      Response { ctx => Task.suspend { a(ctx).flatMap(f andThen (_(ctx))) }}
    def attempt[A](a: Response[A]): Response[Throwable \/ A] = a.attempt
    def fail[A](err: Throwable): Response[A] = Response.fail(err)

    def chooseAny[A](head: Response[A], tail: Seq[Response[A]]): Response[(A, Seq[Response[A]])] =
      Response { ctx => Nondeterminism[Task].chooseAny(head(ctx), tail.map(_(ctx))).map {
        case (a, rem) => (a, rem.map(Response.async(_))) }
      }

    override def gatherUnordered[A](rs: Seq[Response[A]]): Response[List[A]] = Response { ctx =>
      Task.suspend { Nondeterminism[Task].gatherUnordered(rs.map(_(ctx))) }
    }
  }

  /** Gather the results of multiple responses in parallel, preserving the order of the results. */
  def gather[A](rs: Seq[Response[A]]): Response[List[A]] =
    Nondeterminism[Response].gather(rs)

  /** An `Applicative[Response]` in which `apply2`, `apply3`, etc compute results in parallel. */
  val par: Applicative[Response] = new Applicative[Response] {
    def point[A](a: => A): Response[A] = Response.now(a)
    def ap[A,B](a: => Response[A])(f: => Response[A => B]): Response[B] = apply2(f,a)(_(_))
    override def apply2[A,B,C](a: => Response[A], b: => Response[B])(f: (A,B) => C): Response[C] =
      Nondeterminism[Response].mapBoth(a, b)(f)
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

    /** Add the given entries to the `header` of this `Context`, overwriting on collisions. */
    def entries(kvs: (String,String)*): Context = copy(header = header ++ kvs)

    /** Add the given entries to the `header` of this `Context`, overwriting on collisions. */
    def ++(kvs: Iterable[(String,String)]): Context = copy(header = header ++ kvs)
  }
  object Context {
    /** The empty `Context`, contains an empty header and tracing stack. */
    val empty = Context(Map(), List())
  }
}
