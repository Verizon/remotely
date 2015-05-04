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

import scalaz._
import scalaz.concurrent.Task
import scala.concurrent.duration._

case object CircuitBreakerOpen extends Exception

case class BreakerState(halfOpen: Boolean = false, errors: Int = 0, openTime: Option[Long] = None)

class CircuitBreaker(timeout: Duration,
                     maxErrors: Int,
                     breaker: IORef[BreakerState]) { self =>

  def transform: Task ~> Task = new (Task ~> Task) {
    def apply[A](a: Task[A]) = self(a)
  }

  def apply[A](a: Task[A]): Task[A] = {
    def doAttempt: Task[A] = a.onFinish{
      case Some(e) => addFailure
      case None => close
    }
    for {
      s <- breaker.read
      r <- s match {
        // Breaker is closed. Everything is fine.
        case BreakerState(_, _, None) => doAttempt
        // Breaker is open
        case bs@BreakerState(halfOpen, n, Some(t1)) =>
          // Attempt to enter the half-open state
          val t2 = System.currentTimeMillis
          if (t2 - t1 >= timeout.toMillis && !halfOpen)
            breaker.compareAndSet(bs, BreakerState(true, n, Some(t1))).flatMap { b =>
              if (b) doAttempt else apply(a)
            }
          else Task.fail(CircuitBreakerOpen)
      }
    } yield r
  }

  def addFailure: Task[Unit] =
    breaker.modify {
      // We haven't yet reached the breaking point
      case BreakerState(ho, n, None) if (n < maxErrors) =>
        BreakerState(false, n + 1, None)
      // The breaker is closed, but should be opened
      case BreakerState(ho, _, None) =>
        BreakerState(false, 0, Some(System.currentTimeMillis))
      // The breaker is open. A non-failfast error just happened, so we reset the time.
      case BreakerState(ho, n, Some(t)) =>
        BreakerState(false, n + 1, Some(System.currentTimeMillis))
    }

  def close: Task[Unit] = breaker.write(BreakerState())
}

object CircuitBreaker {
  def apply(timeout: Duration, maxErrors: Int): CircuitBreaker =
    new CircuitBreaker(timeout, maxErrors, IORef(BreakerState()))
}
