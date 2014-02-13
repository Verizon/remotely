package srpc

import scala.reflect.runtime.universe.TypeTag
import scalaz.concurrent.Task
import scodec._

trait Remoteable[A] {
  def apply(a: A): Remote[A]
}

trait LowPriority {
  implicit def codecIsRemoteable[A:Codec:TypeTag]: Remoteable[A] =
    Remoteable.encoderIsRemoteable[A]
}

object Remoteable extends LowPriority {

  /**
   * Return the `Remoteable` instance for `A`,
   * from implicit scope.
   */
  def apply[A](implicit R: Remoteable[A]): Remoteable[A] = R

  implicit def encoderIsRemoteable[A:Encoder:TypeTag]: Remoteable[A] =
    new Remoteable[A] {
      def apply(a: A) = Remote.Local(a, Some(Encoder[A]), Remote.toTag[A])
    }

  implicit def taskToRemote[A:Encoder:TypeTag](t: Task[A]): Remote[A] =
    Remote.Async(t, Encoder[A], Remote.toTag[A])

  implicit def toRemote[A:Remoteable](a: A): Remote[A] =
    Remoteable[A].apply(a)
}
