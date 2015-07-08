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

import java.net.InetSocketAddress
import java.net.SocketAddress
import scala.concurrent.duration._
import scalaz.{\/-, \/}
import scalaz.concurrent.Task
import scalaz.stream.Process

/**
 * A collection of callbacks that can be passed to `[[remotely.Environment#serve]]`
 * to gather statistics about a running RPC server.
 */
trait Monitoring { self =>

  /**
   * Invoked with the request, the request context,
   * the set of names referenced by that request,
   * the result, and how long it took.
   */
  def handled[A](ctx: Response.Context,
                 req: Remote[A],
                 references: Iterable[String],
                 result: Throwable \/ A,
                 took: Duration): Unit

  def handle[A](ctx: Response.Context, req: Remote[A], references: Iterable[String], result: Throwable \/ A, startTime: Long) = Task.delay {
    val duration = Duration.fromNanos(System.nanoTime() - startTime)
    handled(ctx, req, references, result, duration)
  }

  def negotiating(addr: Option[SocketAddress],
                  what: String,
                  error: Option[Throwable]): Unit

  protected def renderAddress(addr: Option[SocketAddress]): String = addr match {
    case Some(addr) if addr.isInstanceOf[InetSocketAddress] => addr.asInstanceOf[InetSocketAddress].toString
    case _ => "<unknown addr>"
  }

  def sink[A](ctx: Response.Context, req: Remote[A], references: Iterable[String], startTime: Long) = Process.constant { a: A =>
    handle(ctx, req, references, \/-(a), startTime)
  }

  /**
   * Return a new `Monitoring` instance that send statistics
   * to both `this` and `other`.
   */
  def ++(other: Monitoring): Monitoring = new Monitoring {
    override def handled[A](ctx: Response.Context,
                   req: Remote[A],
                   references: Iterable[String],
                   result: Throwable \/ A,
                   took: Duration): Unit = {
      self.handled(ctx, req, references, result, took)
      other.handled(ctx, req, references, result, took)
    }
    override def negotiating(addr: Option[SocketAddress],
                             what: String,
                             error: Option[Throwable]): Unit = {
      self.negotiating(addr, what, error)
      other.negotiating(addr, what, error)
    }
  }

  /**
   * Returns a `Monitoring` instance that records at most one
   * update for `every` elapsed duration.
   */
  def sample(every: Duration): Monitoring = {
    val nextUpdate = new java.util.concurrent.atomic.AtomicLong(System.nanoTime + every.toNanos)
    new Monitoring {

      def maybe(t: => Unit) {
        val cur = nextUpdate.get
        if (System.nanoTime > cur) {
          t
          // only one thread gets to bump this
          nextUpdate.compareAndSet(cur, cur + every.toNanos)
          ()
        } else ()

      }

      override def handled[A](ctx: Response.Context,
                     req: Remote[A],
                     references: Iterable[String],
                     result: Throwable \/ A,
                     took: Duration): Unit = maybe(self.handled(ctx, req, references, result, took))

      override def negotiating(addr: Option[SocketAddress],
                               what: String,
                               error: Option[Throwable]): Unit = maybe(self.negotiating(addr,what,error))
    }
  }
}

object Monitoring {

  /** The `Monitoring` instance that just ignores all inputs. */
  val empty: Monitoring = new Monitoring {
    override def handled[A](ctx: Response.Context,
                            req: Remote[A],
                            references: Iterable[String],
                            result: Throwable \/ A,
                            took: Duration): Unit = ()

    override def negotiating(addr: Option[SocketAddress], what: String, error: Option[Throwable]): Unit = ()
  }

  /**
   * A `Monitoring` instance that logs all requests to the console.
   * Useful for debugging. All lines output will begin with the given
   * prefix.
   */
  def consoleLogger(prefix: String = ""): Monitoring = new Monitoring {
    def handled[A](ctx: Response.Context,
                   req: Remote[A],
                   references: Iterable[String],
                   result: Throwable \/ A,
                   took: Duration): Unit = {
      println(s"$prefix ----------------")
      println(s"$prefix header: " + ctx.header)
      println(s"$prefix trace: " + ctx.stack.mkString(" "))
      println(s"$prefix request: " + req)
      println(s"$prefix result: " + result)
      println(s"$prefix duration: " + took)
    }

    override def negotiating(addr: Option[SocketAddress],
                             what: String,
                             error: Option[Throwable]): Unit = {
      error.fold(println(s"$prefix NEGOTIATION - $what with ${renderAddress(addr)}")){e =>
        println(s"$prefix NEGOTIATION FAILURE - $what with ${renderAddress(addr)}: ${e.getMessage}")
      }
    }
  }
}
