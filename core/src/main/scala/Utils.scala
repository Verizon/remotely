package remotely

import remotely.codecs.DecodingFailure
import scodec.Attempt.{Successful, Failure}
import scodec.{Attempt, Err}

import scalaz.{\/-, -\/, \/}
import scalaz.concurrent.Task

package object utils {
  implicit class AugmentedEither[E,A](a: E \/ A) {
    def toTask(implicit conv: E => Throwable): Task[A] = a match {
      case -\/(e) => Task.fail(conv(e))
      case \/-(a) => Task.now(a)
    }
  }
  implicit def errToE(err: Err) = new DecodingFailure(err)
  implicit class AugmentedAttempt[A](a: Attempt[A]) {
    def toTask(implicit conv: Err => Throwable): Task[A] = a match {
      case Failure(err) => Task.fail(conv(err))
      case Successful(a) => Task.now(a)
    }
  }
}