package remotely

import java.util.NoSuchElementException

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

  implicit class AugmentedTask[A](t: Task[A]) {
    def onComplete(f: Throwable \/ A => Unit): Task[A] =
      t.attempt.flatMap{a => f(a); t}
  }

  implicit class AugmentedProcess[A](p: Process[Task, A]) {
    def flatten[B](implicit conv: A => Process[Task,B]): Process[Task,B] =
      p.flatMap(conv)
    def uncons: Task[(A, Process[Task, A])] = (p pipe Process.await1).runLastOr(throw new NoSuchElementException).map(a => (a, p))
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