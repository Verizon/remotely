package remotely

import org.scalacheck._
import Prop._
import scalaz.concurrent.Task

object RemoteSpec extends Properties("CircuitBreaker") {

  // An endpoint whose connections always fail
  lazy val failures = Endpoint(emit(bs => Task.fail(new Exception("Oops"))).repeat)

  // An endpoint whose

  // Check that the circuit-breaker opens when maxErrors has been reached
  property("circuitBroken opens") {

  }

}
