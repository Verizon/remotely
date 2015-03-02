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

import org.scalacheck._
import Prop._
import scalaz.concurrent.Task
import scodec.bits.ByteVector
import scalaz.stream._
import Process._
import scala.concurrent.duration._
import scalaz._
import scalaz.std.anyVal._
import scalaz.std.tuple._
import \/._

object CircuitBreakerSpec extends Properties("CircuitBreaker") {

  def failures(n: Int, cb: CircuitBreaker) =
    List.fill(n)(cb(Task.fail(new Error("oops"))).attempt).foldLeft(Task.now(right[Throwable, Int](0))) {
      (t1, t2) => t1.flatMap(_ => t2)
    }

  // The CB doesn't open until maxErrors has been reached.
  property("remains-closed") = forAll { (b: Byte) =>
    val x = b.toInt
    val n = x.abs
    val p = for {
      r <- failures(n + 1, CircuitBreaker(3.seconds, n))
    } yield r
    p.run match {
      case -\/(e) => e.getMessage == "oops"
      case _ => false
    }
  }

  // The circuit-breaker opens when maxErrors has been reached.
  // Note that it opens AFTER the error has occurred, so maxErrors=0 will allow
  // one error to go through.
  property("opens") = forAll { (b: Byte) =>
    // Scala!
    val x = b.toInt
    val n = x.abs
    val p = for {
      r <- failures(n + 2, CircuitBreaker(3.seconds, n))
    } yield r
    p.run match {
      case -\/(CircuitBreakerOpen) => true
      case _ => false
    }
  }

  // The CB closes again
  property("closes") = secure {
    val cb = CircuitBreaker(1.milliseconds, 0)
    val p = for {
      _ <- cb(Task.fail(new Error("oops"))).attempt
      _ <- Task(Thread.sleep(2))
      // The breaker should have closed by now
      r <- cb(Task.now(0))
    } yield r
    p.attemptRun.fold(_ => false, _ == 0)
  }

  // The CB doesn't open as long as there are successes
  property("stays-closed") = secure {
    val cb = CircuitBreaker(3.hours, 1)
    val p = for {
      _ <- cb(Task.fail(new Error("oops"))).attempt
      _ <- cb(Task.now(0))
      _ <- cb(Task.fail(new Error("oops"))).attempt
      r <- cb(Task.now(1))
    } yield r
    p.attemptRun.fold(_ => false, _ == 1)
  }

}
