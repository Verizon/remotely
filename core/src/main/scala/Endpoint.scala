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

import java.net.{InetSocketAddress,Socket,URL}
import javax.net.ssl.SSLEngine
import scalaz.std.anyVal._
import scalaz.concurrent.Task
import scalaz.syntax.functor._
import scalaz.stream.{async,Channel,Exchange,io,Process,nio,Process1, process1, Sink}
import scodec.bits.{BitVector}
import scala.concurrent.duration._
import scalaz._
import Process.{Await, Emit, Halt, emit, await, halt, eval, await1, iterate}
import scalaz.stream.merge._

/**
 * A 'logical' endpoint for some service, represented
 * by a possibly rotating stream of `Connection`s.
 */
trait Endpoint extends Handler {
  def apply(in: Process[Task,BitVector]): Process[Task,BitVector]
/*

  /**
    * Adds a circuit-breaker to this endpoint that "opens" (fails fast) after
    * `maxErrors` consecutive failures, and attempts a connection again
    * when `timeout` has passed.
    */
  def circuitBroken(timeout: Duration, maxErrors: Int): Task[Endpoint] =
    CircuitBreaker(timeout, maxErrors).map { cb =>
      Endpoint(connections.map(c => bs => c(bs).translate(cb.transform)))
    }
 */
}
object Endpoint {

  def empty: Endpoint = new Endpoint {
    def apply(in: Process[Task,BitVector]) = Process.fail(new Exception("no available connections"))
  }

  def single(transport: Handler): Endpoint = new Endpoint {
    def apply(in: Process[Task,BitVector]): Process[Task,BitVector] = transport(in)
  }
}


