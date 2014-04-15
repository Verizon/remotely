package remotely

import scalaz._
import scalaz.concurrent.Task
import scala.concurrent.duration._
import java.util.Date

case class BreakerState(halfOpen: Boolean = false, errors: Int = 0, openTime: Option[Long] = None)

class CircuitBreaker(timeout: Duration,
                     maxErrors: Int,
                     breaker: IORef[BreakerState]) { self =>

  def transform: Task ~> Task = new (Task ~> Task) {
    def apply[A](a: Task[A]) = self(a)
  }

  def apply[A](a: Task[A]): Task[A] = {
    def doAttempt: Task[A] = a.attempt.flatMap {
      case -\/(e) => addFailure.flatMap(_ => Task.fail(e))
      case \/-(a) => for {
        _ <- close
        r <- Task.now(a)
      } yield r
    }
    for {
      s <- breaker.read
      r <- s match {
        // Breaker is closed. Everything is fine.
        case BreakerState(_, _, None) => doAttempt
        // Breaker is open
        case bs@BreakerState(halfOpen, n, Some(t1)) =>
          // Attempt to enter the half-open state
          val t2 = new Date().getTime
          if (t2 - t1 >= timeout.toMillis && !halfOpen)
            breaker.compareAndSet(bs, BreakerState(true, n, Some(t1))).flatMap { b =>
              if (b) doAttempt else apply(a)
            }
          else Task.fail(new Exception("Circuit-breaker open"))
      }
    } yield r
  }

  def addFailure: Task[Unit] = for {
    _ <- breaker.modify {
      // We haven't yet reached the breaking point
      case BreakerState(ho, n, None) if (n < maxErrors) =>
        BreakerState(false, n + 1, None)
      // The breaker is closed, but should be opened
      case BreakerState(ho, _, None) =>
        BreakerState(false, 0, Some(new Date().getTime))
      // The breaker is open. A non-failfast error just happened, so we reset the time.
      case BreakerState(ho, n, Some(t)) =>
        BreakerState(false, n + 1, Some(new Date().getTime))
    }
  } yield ()

  def close: Task[Unit] = breaker.write(BreakerState())
}

object CircuitBreaker {
  def apply(timeout: Duration, maxErrors: Int): Task[CircuitBreaker] =
    IORef(BreakerState()).map(new CircuitBreaker(timeout, maxErrors, _))
}
