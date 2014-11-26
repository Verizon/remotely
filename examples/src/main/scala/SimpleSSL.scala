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
package examples

import java.net.InetSocketAddress
import scalaz.concurrent.Task
import transport.netty._

import codecs._

/* stew: commenting this out until we have an SSL tranport

object SimpleSSL extends App {

  import Simple.{env,addr,sum}
  import Remote.implicits._

  println(env)

  val clientCtx = SSLSetup.context(
    "Paul-test-client",
    "ssl-testing/client_cert.pem",
    "ssl-testing/client_key.pem",
    "ssl-testing/CA.pem").run

  val serverCtx = SSLSetup.context(
    "Paul-test-server",
    "ssl-testing/server_cert.pem",
    "ssl-testing/server_key.pem",
    "ssl-testing/CA.pem").run

  val serverSslProvider = tls.enableCiphers(tls.ciphers.rsa: _*)(tls.fromContext(serverCtx))
  val clientSslProvider = tls.enableCiphers(tls.ciphers.rsa: _*)(tls.fromContext(clientCtx))

  // create a server for this environment
  val server = env.serveNettySSL(addr, tls.server(serverSslProvider))(Monitoring.consoleLogger("[server]"))

  try {
    val expr: Remote[Int] = sum(List(0,1,2,3,4))
    val loc: Endpoint = Endpoint.single(NettyTransport.singleSSL(tls.client(clientSslProvider))(clientPool,addr))
    val result: Task[Int] = expr.runWithContext(loc, Response.Context.empty, Monitoring.consoleLogger("[client]"))

    // running a couple times just to see the latency improve for subsequent reqs
    println { result.run; result.run; result.run }
  }
  finally {
    // hack to allow asynchronous actor shutdown messages to propagate,
    // without this, we get some dead letter logging
    // I'm sure there's a better way to do this
    println("shutting down...")
    Thread.sleep(1000)
    server()
    println("..done shutting down")
  }
}

*/
