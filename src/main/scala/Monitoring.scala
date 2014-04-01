package remotely

import scala.concurrent.duration._
import scalaz.\/

/**
 * A collection of callbacks that can be passed to `[[remotely.Server.start]]`
 * to gather statistics about a running RPC server.
 */
trait Monitoring { self =>

  /**
   * Invoked with the request, the set of names referenced by that
   * request, the result, and how long it took.
   */
  def handled[A](req: Remote[A],
                 references: Iterable[String],
                 result: Throwable \/ A,
                 took: Duration): Unit

  /**
   * Return a new `Monitoring` instance that send statistics
   * to both `this` and `other`.
   */
  def ++(other: Monitoring): Monitoring = new Monitoring {
    def handled[A](req: Remote[A],
                   references: Iterable[String],
                   result: Throwable \/ A,
                   took: Duration): Unit = {
      self.handled(req, references, result, took)
      other.handled(req, references, result, took)
    }
  }

  /**
   * Returns a `Monitoring` instance that records at most one
   * update for `every` elapsed duration.
   */
  def sample(every: Duration): Monitoring = {
    val nextUpdate = new java.util.concurrent.atomic.AtomicLong(System.nanoTime + every.toNanos)
    new Monitoring {
      def handled[A](req: Remote[A],
                     references: Iterable[String],
                     result: Throwable \/ A,
                     took: Duration): Unit = {
        val cur = nextUpdate.get
        if (System.nanoTime > cur) {
          self.handled(req, references, result, took)
          // only one thread gets to bump this
          nextUpdate.compareAndSet(cur, cur + every.toNanos)
          ()
        }
        else () // do nothing
      }
    }
  }
}

object Monitoring {

  /** The `Monitoring` instance that just ignores all inputs. */
  val empty: Monitoring = new Monitoring {
    def handled[A](req: Remote[A],
                   references: Iterable[String],
                   result: Throwable \/ A,
                   took: Duration): Unit = ()
  }

  /**
   * A `Monitoring` instance that logs all requests to the console.
   * Useful for debugging.
   */
  val consoleLogger: Monitoring = new Monitoring {
    def handled[A](req: Remote[A],
                   references: Iterable[String],
                   result: Throwable \/ A,
                   took: Duration): Unit = {
      println("----------------")
      println("request: " + req)
      println("result: " + result)
      println("duration: " + took)
    }
  }
}
