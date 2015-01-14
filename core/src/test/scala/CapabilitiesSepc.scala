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

import java.util.concurrent.Executors
import org.scalatest.matchers.{Matcher,MatchResult}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import remotely.transport.netty._
import scala.concurrent.duration.DurationInt
import scalaz.stream.Process
import scalaz.concurrent.{Strategy,Task}
import codecs._

class CapabilitiesSpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {
  
  val addr1 = new java.net.InetSocketAddress("localhost", 9003)

  val server1 = new CountServer
  val shutdown1: Task[Unit] = server1.environment.serveNetty(addr1, capabilities = Capabilities(Set()))

  override def afterAll() {
    shutdown1.run
  }
  val endpoint1 = Endpoint.single(NettyTransport.single(addr1))

  behavior of "Capabilities"
  
  it should "not call an incompatible server" in {
    import Response.Context
    import Remote.implicits._
    import codecs._

    an[IncompatibleServer] should be thrownBy {
      val _ = evaluate(endpoint1, Monitoring.empty)(CountClient.ping(1)).apply(Context.empty).run
    }
  }
}

