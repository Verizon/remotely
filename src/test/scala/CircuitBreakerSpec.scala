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
    List.fill(n)(cb(fail).attempt).foldLeft(Task.now(right[Throwable, Int](0))) {
      (t1, t2) => t1.flatMap(_ => t2)
    }

  val fail: Task[Int] = Task.fail(new RuntimeException("oops"))

  // The CB doesn't open until maxErrors has been reached.
  property("remains-closed") = forAll { (b: Byte) =>
    val x = b.toInt
    val n = x.abs
    val p = for {
      cb <- CircuitBreaker(3.seconds, n)
      r <- failures(n + 1, cb)
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
      cb <- CircuitBreaker(3.seconds, n)
      r <- failures(n + 2, cb)
    } yield r
    p.run match {
      case -\/(CircuitBreakerOpen) => true
      case _ => false
    }
  }

  // The CB closes again
  property("closes") = {
    val p = for {
      _ <- Task(println("I'm starting"))
      cb <- CircuitBreaker(3.seconds, 1)
      _ <- Task(println("Breaking"))
      // This should make it open
      _ <- cb(fail).attempt
      _ <- Task(println("I'm sleeping"))
      _ <- Task(Thread.sleep(3100))
      _ <- Task(println("I'm up"))
      // Should by now have entered the half-open state
      r <- cb(Task.now(0)).attempt
      _ <- Task(println("More up"))
    } yield r
    p.run.fold(_ => false, _ == 0)
  }

  // The CB closes after the timeout

  // The CB doesn't open as long as there are successes

}
