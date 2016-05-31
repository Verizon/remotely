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

import java.net.{InetSocketAddress,Socket,URL}
import javax.net.ssl.SSLEngine
import scalaz.std.anyVal._
import scalaz.concurrent.{Strategy,Task}
import scalaz.syntax.functor._
import scalaz.stream.{async,Channel,Exchange,io,Process,nio,Process1, process1, Sink}
import scodec.bits.{BitVector}
import scala.concurrent.duration._
import scalaz._
import Process.{Await, Emit, Halt, emit, await, halt, eval, await1, iterate}
import scalaz.stream.merge._

/**
 * A 'logical' endpoint for some service, represented
 * by a possibly rotating stream of `Transport`s.
 */
case class Endpoint(connections: Process[Task,Handler]) {
  def get: Task[Handler] = connections.once.runLast.flatMap {
    case None => Task.fail(new Exception("No available connections"))
    case Some(a) => Task.now(a)
  }


  /**
    * Adds a circuit-breaker to this endpoint that "opens" (fails fast) after
    * `maxErrors` consecutive failures, and attempts a connection again
    * when `timeout` has passed.
    */
  def circuitBroken(timeout: Duration, maxErrors: Int): Endpoint =
    Endpoint(connections.map(c => (bs: Process[Task,BitVector]) => c(bs).translate(CircuitBreaker(timeout, maxErrors).transform)))
}

object Endpoint {

  def empty: Endpoint = Endpoint(Process.halt)
  def single(transport: Handler): Endpoint = Endpoint(Process.constant(transport))

  /**
    * If a connection in an endpoint fails, then attempt the same call to the next
    * endpoint, but only if `timeout` has not passed AND we didn't fail in a
    * "committed state", i.e. we haven't received any bytes.
    */
  def failoverChain(timeout: Duration, es: Process[Task, Endpoint]): Endpoint =
    Endpoint(transpose(es.map(_.connections)).flatMap { cs =>
               cs.reduce((c1, c2) => bs => c1(bs) match {
                           case w@Await(a, k, _) =>
                             await(time(a.attempt))((p: (Duration, Throwable \/ Any)) => p match {
                                                      case (d, -\/(e)) =>
                                                        if (timeout - d > 0.milliseconds) c2(bs)
                                                        else eval(Task.fail(new Exception(s"Failover chain timed out after $timeout")))
                                                      case (d, \/-(x)) => k(\/-(x)).run
                                                    })
                           case x => x
                         })
             })

  /**
    * An endpoint backed by a (static) pool of other endpoints.
    * Each endpoint has its own circuit-breaker, and fails over to all the others
    * on failure.
    */
  def uber(maxWait: Duration,
           circuitOpenTime: Duration,
           maxErrors: Int,
           es: Process[Task, Endpoint]): Endpoint = {
    Endpoint(raceHandlerPool(permutations(es).map(ps => failoverChain(maxWait, ps.map(_.circuitBroken(circuitOpenTime, maxErrors))).connections)))
  }

  /**
    * Produce a stream of all the permutations of the given stream.
    */
  private[remotely] def permutations[F[_] : Monad : Catchable,A](p: Process[F, A]): Process[F, Process[F, A]] = {
    val xs = iterate(0)(_ + 1) zip p
    for {
      b <- eval(isEmpty(xs))
      r <- if (b) emit(xs) else for {
        x <- xs
        ps <- permutations(xs |> delete { case (i, v) => i == x._1 })
      } yield emit(x) ++ ps
    } yield r.map(_._2)
  }

  /** Skips the first element that matches the predicate. */
  private[remotely] def delete[I](f: I => Boolean): Process1[I,I] = {
    def go(s: Boolean): Process1[I,I] =
      await1[I] flatMap (i => if (s && f(i)) go(false) else emit(i) ++ go(s))
    go(true)
  }

  /**
    * Transpose a process of processes to emit all their first elements, then all their second
    * elements, and so on.
    */
  private[remotely] def transpose[F[_]:Monad: Catchable, A](as: Process[F, Process[F, A]]): Process[F, Process[F, A]] =
    emit(as.flatMap(_.take(1))) ++ eval(isEmpty(as.flatMap(_.drop(1)))).flatMap(b =>
      if(b) halt else transpose(as.map(_.drop(1))))

  /**
    * Returns true if the given process never emits.
    */
  private[remotely] def isEmpty[F[_]: Monad: Catchable, O](p: Process[F, O]): F[Boolean] = ((p as false) |> Process.await1).runLast.map(_.getOrElse(true))

  private def time[A](task: Task[A]): Task[(Duration, A)] = for {
    t1 <- Task.delay(System.currentTimeMillis)
    a  <- task
    t2 <- Task.delay(System.currentTimeMillis)
  } yield ((t2 - t1).milliseconds, a)

  private object raceHandlerPool {
    implicit val s = Strategy.Executor(fixedNamedThreadPool("endpointHandlerPool"))
    def apply(pool: Process[Task, Process[Task, Handler]]): Process[Task, Handler] = mergeN(pool)
  }
}


