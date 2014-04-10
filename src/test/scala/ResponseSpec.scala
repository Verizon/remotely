package remotely

import org.scalacheck._
import Prop._
import scala.concurrent.{ExecutionContext,Future}
import scalaz.Monad

object ResponseSpec extends Properties("Response") {

  property("stack safety") = {
    import ExecutionContext.Implicits.global
    val N = 100000
    val responses = (0 until N).map(Monad[Response].pure(_))
    val responses2 = (0 until N).map(i => Response.async(Future(i)))

    def leftFold(responses: Seq[Response[Int]]): Response[Int] =
      responses.foldLeft(Monad[Response].pure(0))(Monad[Response].apply2(_,_)(_ + _))
    def rightFold(responses: Seq[Response[Int]]): Response[Int] =
      responses.reverse.foldLeft(Monad[Response].pure(0))((tl,hd) => Monad[Response].apply2(hd,tl)(_ + _))

    val ctx = Response.Context.empty
    val expected = (0 until N).sum

    leftFold(responses)(ctx).run == expected &&
    rightFold(responses)(ctx).run == expected &&
    leftFold(responses2)(ctx).run == expected &&
    rightFold(responses2)(ctx).run == expected
  }
}
