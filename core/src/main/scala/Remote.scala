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

import scala.collection.immutable.SortedSet
import scala.reflect.runtime.universe._
import scalaz.concurrent.Task
import scalaz.{\/, Applicative, Monad, Nondeterminism}
import scala.reflect.runtime.universe.TypeTag
import scodec.{Codec,Decoder,Encoder}
import scodec.bits.BitVector
import shapeless._
import scalaz.stream.Process

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

  /**
   * Builds a List by applying a partial function to all children of this Remote on which the function is defined.
   */
  def collect[B](partial: PartialFunction[Remote[Any],B]): List[B] = {
    val me = partial.lift(this).toList
    val rest = this match {
      case app: Remote.Ap[_] => (app.f :: app.args).map(_.collect(partial)).flatten
      case _ => Nil
    }
    me ++ rest
  }
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

  def localStream[A: Encoder:TypeTag](stream: Process[Task, A]): Remote[Process[Task,A]] =
    Remote.LocalStream(stream, Some(Encoder[A]), Remote.toTag[A])

  implicit class RunSyntaxForStreaming[A](self: Remote[Process[Task,A]]) {
    /** Call `self.run(at, M).apply(ctx)` to get back a `Task[A]`. */
    def run(at: Endpoint, ctx: Response.Context = Response.Context.empty, M: Monitoring = Monitoring.empty)(implicit A: TypeTag[A], C: Codec[A]): Task[Process[Task,A]] =
      evaluateStream(at,M)(self).apply(ctx)
  }

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

    /** Run this with an empty context */
    def runWithoutContext(at: Endpoint, M: Monitoring = Monitoring.empty)(implicit A: TypeTag[A], C: Codec[A]): Task[A] =
      runWithContext(at, Response.Context.empty)
  }
  implicit class Ap1Syntax[A,B](self: Remote[A => B]) {
    def apply(a: Remote[A]): Remote[B] =
      Remote.Ap1(self, a)
  }
  // By declaring this as opposed to an implicit conversion from Stream to a Remote Stream
  // we are limiting the use of streaming to functions with a single argument
  // That is because for now, I am not too clear on the desired semantics for a function that takes
  // multiple Streams as arguments.
  implicit class ApStream1Syntax[A:Encoder:TypeTag,B](self: Remote[Process[Task,A] => B]) {
    def apply(stream: Process[Task,A]): Remote[B] =
      Remote.Ap1(self, LocalStream(stream, Some(Encoder[A]), Remote.toTag[A]))
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

  private[remotely] case class LocalStream[A](
    stream: Process[Task,A],
    format: Option[Encoder[A]],
    tag: String
  ) extends Remote[Process[Task,A]] {
    override def toString = stream.toString
  }

  /**
   * Reference to a remote value on the server.
   */
  private[remotely] case class Ref[A](name: String) extends Remote[A] {
    override def toString = name.takeWhile(_ != ':')
  }

  private[remotely] abstract class Ap[A](val f: Remote[Any],val args: List[Remote[Any]]) extends Remote[A] {
    override def toString = s"""$f({$args.mkString(","})"""
  }

  // we require a separate constructor for each function
  // arity, since remote invocations must be fully saturated
  private[remotely] case class Ap1[A,B](
    override val f: Remote[A => B],
    a: Remote[A]) extends Ap[B](f,List(a))

  private[remotely] case class Ap2[A,B,C](
    override val f: Remote[(A,B) => C],
    a: Remote[A],
    b: Remote[B]) extends Ap[C](f,List(a,b))

  private[remotely] case class Ap3[A,B,C,D](
    override val f: Remote[(A,B,C) => D],
    a: Remote[A],
    b: Remote[B],
    c: Remote[C]) extends Ap[D](f,List(a,b,c))

  private[remotely] case class Ap4[A,B,C,D,E](
    override val f: Remote[(A,B,C,D) => E],
    a: Remote[A],
    b: Remote[B],
    c: Remote[C],
    d: Remote[D]) extends Ap[E](f,List(a,b,c,d))

  /** Collect up all the `Ref` names referenced by `r`. */
  def refs[A](r: Remote[A]): SortedSet[String] = (r collect {
    case Ref(t) => SortedSet(t)
  }).fold(SortedSet.empty[String])(_.union(_))

  /** Collect up all the formats referenced by `r`. */
  def formats[A](r: Remote[A]): SortedSet[String] = r.collect {
    case Local(a,e,t) => SortedSet(t)
  }.fold(SortedSet.empty[String])(_.union(_))

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

    /** Implicitly promote a local value to a `Remote[A]`. */
    implicit def localToRemote[A:Encoder:TypeTag](a: A): Remote[A] = local(a)

    // Commented out because for now cannot call a function that takes multiple streams
    //implicit def localStreamToRemote[A: Encoder:TypeTag](stream: Process[Task, A]): Remote[Process[Task,A]] = localStream(stream)
  }
}

