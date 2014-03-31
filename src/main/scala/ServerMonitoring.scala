package remotely

import scala.concurrent.duration._
import scalaz.\/

/**
 * A collection of callbacks that can be passed to `[[remotely.Server.start]]`
 * to gather statistics about a running RPC server.
 */
trait ServerMonitoring { self =>

  /**
   * Invoked with the request, the set of names referenced by that
   * request, the result, and how long it took.
   */
  def handled[A](req: Remote[A],
                 references: Iterable[String],
                 result: Throwable \/ A,
                 took: Duration): Unit

  /**
   * Return a new `ServerMonitoring` instance that send statistics
   * to both `this` and `other`.
   */
  def ++(other: ServerMonitoring): ServerMonitoring = new ServerMonitoring {
    def handled[A](req: Remote[A],
                   references: Iterable[String],
                   result: Throwable \/ A,
                   took: Duration): Unit = {
      self.handled(req, references, result, took)
      other.handled(req, references, result, took)
    }
  }

  /**
   * Returns a `ServerMonitoring` instance that records at most one
   * update for `every` elapsed duration.
   */
  def sample(every: Duration): ServerMonitoring = {
    val nextUpdate = new java.util.concurrent.atomic.AtomicLong(System.nanoTime + every.toNanos)
    new ServerMonitoring {
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

object ServerMonitoring {

  /** The `ServerMonitoring` instance that just ignores all inputs. */
  val empty: ServerMonitoring = new ServerMonitoring {
    def handled[A](req: Remote[A],
                   references: Iterable[String],
                   result: Throwable \/ A,
                   took: Duration): Unit = ()
  }

  /**
   * A `ServerMonitoring` instance that logs all requests to the console.
   * Useful for debugging.
   */
  val consoleLogger: ServerMonitoring = new ServerMonitoring {
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
