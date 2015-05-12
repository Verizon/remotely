package remotely

import java.util.NoSuchElementException

import org.scalatest.{Matchers, WordSpec}
import scalaz.concurrent.Task
import scalaz.stream.Process
import utils._
import scala.concurrent.duration._

class UtilsSpec extends WordSpec with Matchers {
  "uncons" should {
    "work with a constant stream" in {
      val process: Process[Task, Int] = Process(1,2,3)
      val result = process.uncons
      result.run shouldEqual((1, Process(2,3)))
    }
    "work with an async stream v1" in {
      val task = Task.now(1)
      val process = Process.await(task)(Process.emit(_) ++ Process(2,3))
      val result = process.uncons
      val (a, newProcess) = result.run
      a shouldEqual(1)
      newProcess.runLog.run shouldEqual(Seq(2,3))
    }
    "work with an async stream1 v2" in {
      val task = Task.now(1)
      val process = Process.await(task)(a => Process(2,3).prepend(Seq(a)))
      val result = process.uncons
      val (a, newProcess) = result.run
      a shouldEqual(1)
      newProcess.runLog.run shouldEqual(Seq(2,3))
    }
    "work with a queue" in {
      import scalaz.stream.async
      val q = async.unboundedQueue[Int]
      val process = q.dequeue
      val result = process.uncons
      q.enqueueAll(List(1,2,3)).timed(1.second).run
      q.close.run
      val (a, newProcess) = result.timed(1.second).run
      a shouldEqual(1)
      newProcess.runLog.timed(1.second).run shouldEqual(Seq(2,3))
    }
    "throw a NoSuchElementException if Process is empty" in {
      val process = Process.empty[Task, Int]
      val result = process.uncons
      a [NoSuchElementException] should be thrownBy(result.run)
    }
    "propogate failure if stream fails" in {
      val process = Process.fail(new Exception)
      val result = process.uncons
      a [Exception] should be thrownBy(result.run)
    }
  }
}
