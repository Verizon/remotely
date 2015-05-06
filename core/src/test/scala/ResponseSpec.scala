//: ----------------------------------------------------------------------------
//: Copyright (C) 2014 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------

package remotely

import org.scalacheck._
import Prop._
import scala.concurrent.{ExecutionContext,Future}
import scalaz.Monad

object ResponseSpec extends Properties("Response") {

  property("stack safety") = {
    import ExecutionContext.Implicits.global
    // TODO: Figure out if it's possible to bring up this number to it's original 100 000
    val N = 1000
    val responses = (0 until N).map(Monad[SingleResponse].pure(_))
    val responses2 = (0 until N).map(i => Response.async(Future(i)))

    def leftFold(responses: Seq[SingleResponse[Int]]): SingleResponse[Int] =
      responses.foldLeft(Monad[SingleResponse].pure(0))(Monad[SingleResponse].apply2(_,_)(_ + _))
    def rightFold(responses: Seq[SingleResponse[Int]]): SingleResponse[Int] =
      responses.reverse.foldLeft(Monad[SingleResponse].pure(0))((tl,hd) => Monad[SingleResponse].apply2(hd,tl)(_ + _))

    val ctx = Response.Context.empty
    val expected = (0 until N).sum

    leftFold(responses)(ctx).run == expected &&
    rightFold(responses)(ctx).run == expected &&
    leftFold(responses2)(ctx).run == expected &&
    rightFold(responses2)(ctx).run == expected
  }
}
