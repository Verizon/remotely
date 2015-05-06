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
import scalaz.stream.Process

import Response.Context

/** The result type for a remote computation. */
sealed trait Response[+A] {
  def toStream: StreamResponse[Any]
}
sealed trait StreamResponse[+A] extends Response[Process[Task,A]]{
  def apply(c: Context): Process[Task,A]

  def flatMap[B](f: A => StreamResponse[B]): StreamResponse[B] =
    StreamResponse { ctx => Process.suspend(this(ctx).flatMap(f andThen (_(ctx)))) }

  def map[B](f: A => B): StreamResponse[B] =
    StreamResponse { ctx => this(ctx) map f }

  def attempt: StreamResponse[Throwable \/ A] =
    StreamResponse { ctx => this(ctx).attempt() }

  /** Modify the asychronous result of this `Response`. */
  def edit[B](f: Process[Task,A] => Process[Task,B]): StreamResponse[B] =
    StreamResponse { ctx => f(this.apply(ctx)) }

  def toStream = this
}
sealed trait SingleResponse[+A] extends Response[A]{
  def apply(c: Context): Task[A]

  def flatMap[B](f: A => SingleResponse[B]): SingleResponse[B] =
    SingleResponse { ctx => Task.suspend(this(ctx).flatMap(f andThen (_(ctx)))) }

  def map[B](f: A => B): SingleResponse[B] =
    SingleResponse { ctx => this(ctx) map f }

  def attempt: SingleResponse[Throwable \/ A] =
    SingleResponse { ctx => this(ctx).attempt }

  /** Modify the asychronous result of this `Response`. */
  def edit[B](f: Task[A] => Task[B]): SingleResponse[B] =
    SingleResponse { ctx => f(this.apply(ctx)) }

  def toStream: StreamResponse[A] =
    StreamResponse( ctx => Process.eval(this(ctx)))
}

object SingleResponse {
  /** Monad instance for `SingleResponse`. */
  implicit val instance = new Monad[SingleResponse] with Catchable[SingleResponse] {
    def point[A](a: => A): SingleResponse[A] = {
      lazy val result = a // memoized - `point` should not be used for side effects
      SingleResponse(_ => Task.now(result))
    }
    def bind[A,B](a: SingleResponse[A])(f: A => SingleResponse[B]): SingleResponse[B] = a.flatMap(f)
    def attempt[A](a: SingleResponse[A]): SingleResponse[Throwable \/ A] = a.attempt
    def fail[A](err: Throwable): SingleResponse[A] = SingleResponse(_ => Task.fail(err))
  }

  def apply[A](f: Context => Task[A]): SingleResponse[A] = new SingleResponse[A] {
    def apply(c: Context): Task[A] = f(c)
  }

  /** Obtain the current `Context`. */
  def ask: SingleResponse[Context] = SingleResponse { ctx => Task.now(ctx) }

  def scope[A](resp: SingleResponse[A]): SingleResponse[A] =
    SingleResponse(ctx => resp(ctx.push(Response.fresh)))

  def fail(t: Throwable): SingleResponse[Nothing] = SingleResponse(_ => Task.fail(t))

  def suspend[A](resp: SingleResponse[A]) =
    SingleResponse { ctx => Task.suspend(resp(ctx))}
}

object StreamResponse {
  /** Monad instance for `StreamResponse`. */
  implicit val instance = new Monad[StreamResponse] with Catchable[StreamResponse] {
    def point[A](a: => A): StreamResponse[A] = {
      lazy val result = a // memoized - `point` should not be used for side effects
      StreamResponse(_ => Process.emit(result))
    }
    def bind[A,B](a: StreamResponse[A])(f: A => StreamResponse[B]): StreamResponse[B] = a.flatMap(f)
    def attempt[A](a: StreamResponse[A]): StreamResponse[Throwable \/ A] = a.attempt
    def fail[A](err: Throwable): StreamResponse[A] = StreamResponse(_ => Process.fail(err))
  }

  def apply[A](f: Context => Process[Task,A]): StreamResponse[A] = new StreamResponse[A] {
    def apply(c: Context): Process[Task,A] = f(c)
  }

  /** Obtain the current `Context`. */
  def ask: StreamResponse[Context] = StreamResponse { ctx => Process.emit(ctx) }

  def scope[A](resp: StreamResponse[A]): StreamResponse[A] =
    StreamResponse(ctx => resp(ctx.push(Response.fresh)))

  def fail(t: Throwable): StreamResponse[Nothing] = StreamResponse(_ => Process.fail(t))
}

object Response {

  /** Produce a `Response[A]` from a strict value. */
  def now[A](a: A): SingleResponse[A] = SingleResponse { _ => Task.now(a) }

  def stream[A](stream: Process[Task, A]): StreamResponse[A] = StreamResponse { _ => stream }

  /**
   * Produce a `Response[A]` from a nonstrict value, whose result
   * will not be cached if this `Response` is used more than once.
   */
  def delay[A](a: => A): SingleResponse[A] = SingleResponse { _ => Task.delay(a) }

  /**
   * Create a response by registering a completion callback with the given
   * asynchronous function, `register`.
   */
  def async[A](register: ((Throwable \/ A) => Unit) => Unit): SingleResponse[A] =
    SingleResponse { _ => Task.async { register }}

  /** Create a `Response[A]` from a `Task[A]`. */
  def async[A](a: Task[A]): SingleResponse[A] = SingleResponse { _ => a }

  /** Alias for [[remotely.Response.fromFuture]]. */
  def async[A](f: scala.concurrent.Future[A])(implicit E: scala.concurrent.ExecutionContext): SingleResponse[A] =
    fromFuture(f)

  /**
   * Create a response from a `Future`. Note that since `Future` caches its
   * result, it is not safe to reuse this `Response` to repeat the same
   * computation.
   */
  def fromFuture[A](f: scala.concurrent.Future[A])(implicit E: scala.concurrent.ExecutionContext): SingleResponse[A] =
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
  def fresh: ID = new ID { val get = UUID.randomUUID }

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
