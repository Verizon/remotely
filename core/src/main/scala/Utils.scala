package remotely

import remotely.codecs.DecodingFailure
import scodec.Err

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
}