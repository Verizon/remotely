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

import java.util.concurrent.atomic.AtomicInteger
import codecs._

trait CountServerBase {
  import Codecs._

  def ping: Int => Response[Int]
  def describe: Response[List[Signature]]

  def environment = Environment(
    Codecs.empty.codec[Int],
    populateDeclarations(Values.empty)
  )

  private def populateDeclarations(env: Values): Values = env
    .declare("ping", ping)
    .declare("describe", describe)
}

class CountServer extends CountServerBase {
  implicit val intcodec = int32
  val count: AtomicInteger = new AtomicInteger(0)
  def ping: Int => Response[Int] = { _ =>
    val r = count.incrementAndGet()
    Response.delay(r)
  }

  def describe: Response[List[Signature]] = Response.now(List(Signature("describe", "describe: scala.List[Signature]", Nil, "scala.List[Signature]"),
                                                              Signature("ping", "ping: Int => Int", List("Int"), "Int")))
}

object CountClient {
  val ping = Remote.ref[Int => Int]("ping")
}
