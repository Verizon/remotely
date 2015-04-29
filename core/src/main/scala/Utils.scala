package remotely

import remotely.codecs.DecodingFailure
import scodec.Attempt.{Successful, Failure}
import scodec.{Attempt, Err}

import scalaz.{\/-, -\/, \/}
import scalaz.concurrent.Task
import scalaz.stream.Process

package object utils {
  implicit class AugmentedEither[E,A](a: E \/ A) {
    def toTask(implicit conv: E => Throwable): Task[A] = a match {
      case -\/(e) => Task.fail(conv(e))
      case \/-(a) => Task.now(a)
    }
    def toProcess(implicit conv: E => Throwable): Process[Task, A] = a match {
      case -\/(e) => Process.fail(conv(e))
      case \/-(a) => Process.emit(a)
    }
  }

  implicit class AugmentedProcess[A](p: Process[Task, A]) {
    def head: Task[A] = (p pipe Process.await1).runLast.map(_.get)
    def flatten[B](implicit conv: A => Process[Task,B]): Process[Task,B] =
      p.flatMap(conv)
  }
  implicit def errToE(err: Err) = new DecodingFailure(err)
  implicit class AugmentedAttempt[A](a: Attempt[A]) {
    def toTask(implicit conv: Err => Throwable): Task[A] = a match {
      case Failure(err) => Task.fail(conv(err))
      case Successful(a) => Task.now(a)
    }
    def toProcess(implicit conv: Err => Throwable): Process[Task,A] = a match {
      case Failure(err) => Process.fail(conv(err))
      case Successful(a) => Process.emit(a)
    }
  }
}