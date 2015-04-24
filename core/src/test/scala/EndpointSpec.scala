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

import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import remotely.transport.netty.NettyTransport

import scala.concurrent.duration.DurationInt
import scalaz.stream.Process

class EndpointSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  behavior of "failoverChain"
  it should "work" in {
    val goodAddress = new java.net.InetSocketAddress("localhost", 9007)
    val badAddress = new java.net.InetSocketAddress("localhost", 9009)

    val goodEndpoint = (NettyTransport.single(goodAddress) map Endpoint.single).run
    val badEndpoint = (NettyTransport.single(badAddress) map Endpoint.single).run

    def endpoints: Process[Nothing,Endpoint] = Process.emitAll(List(badEndpoint, goodEndpoint))

    val server = new CountServer

    val shutdown = server.environment.serve(goodAddress).run

    val endpoint = Endpoint.failoverChain(10.seconds, endpoints)

    import Response.Context
    import Remote.implicits._
    import codecs._

    val call = evaluate(endpoint, Monitoring.empty)(CountClient.ping(1))

    val i: Int = call.apply(Context.empty).run
    val j: Int = call.apply(Context.empty).run
    j should be (2)

    shutdown.run
  }
}
