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
package test

import java.lang.Process

import org.scalatest.{Matchers, FlatSpec}
import remotely.transport.netty.NettyTransport

class ServerCodecMissingSpec extends FlatSpec with Matchers {
  behavior of "missing codec on the server"
  it should "throw the appropriate error if missing encoder for the response" in {
    val address = new java.net.InetSocketAddress("localhost", 9013)

    val endpoint = (NettyTransport.single(address) map Endpoint.single).run

    val server = new CountServer

    val shutdown = server.environment.serve(address).run

    import Remote.implicits._
    import codecs._

    val call = Remote.local(true).runWithoutContext(endpoint)

    val thrown = the [ServerException] thrownBy call.run

    thrown.getMessage should startWith (s"[decoding] server does not have response serializer for: ${Remote.toTag[Boolean]}")

    shutdown.run
  }
}
