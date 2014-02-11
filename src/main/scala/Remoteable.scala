package srpc

import scala.reflect.ClassManifest
import scalaz.concurrent.Task
import scodec._

trait Remoteable[A] {
  def apply(a: A): Remote[A]
}

object Remoteable {

  /**
   * Return the `Remoteable` instance for `A`,
   * from implicit scope.
   */
  def apply[A](implicit R: Remoteable[A]): Remoteable[A] = R

  implicit def codecIsRemoteable[A:Codec:ClassManifest]: Remoteable[A] =
    new Remoteable[A] {
      def apply(a: A) =
        Remote.Local(a, Codec[A], implicitly[ClassManifest[A]])
    }

  implicit def taskToRemote[A:Codec:ClassManifest](t: Task[A]): Remote[A] =
    Remote.Async(t, Codec[A], implicitly[ClassManifest[A]])

  implicit def toRemote[A:Remoteable](a: A): Remote[A] =
    Remoteable[A].apply(a)
}
