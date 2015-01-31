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

import java.net.InetSocketAddress
import javax.net.ssl.SSLEngine
import scala.reflect.runtime.universe.TypeTag
import scodec.{Codec,Decoder,Encoder}
import scodec.bits.{BitVector}
import scalaz.stream.Process
import scalaz.concurrent.{Strategy,Task}
import scala.concurrent.duration.DurationInt

/**
 * A collection of codecs and values, which can be populated
 * and then served over RPC.
 *
 * Example: {{{
 *   val env: Environment = Environment.empty
 *     .codec[Int]
 *     .codec[List[Int]]
 *     .populate { _
 *        .declareStrict[List[Int] => Int]("sum", _.sum)
 *        .declare("fac", (n: Int) => Task { (1 to n).product })
 *     }
 *   val stopper = env.serve(new InetSocketAddress("localhost",8080))
 *   ///
 *   stopper() // shutdown the server
 * }}}
 */
case class Environment(codecs: Codecs, values: Values) {

  def codec[A](implicit T: TypeTag[A], C: Codec[A]): Environment =
    this.copy(codecs = codecs.codec[A])

  /**
   * Modify the values inside this `Environment`, using the given function `f`.
   * Example: `Environment.empty.populate { _.declare("x")(Task.now(42)) }`.
   */
  def populate(f: Values => Values): Environment =
    this.copy(values = f(values))

  /** Alias for `this.populate(_ => v)`. */
  def values(v: Values): Environment =
    this.populate(_ => v)

  private def serverHandler(monitoring: Monitoring): Handler = { bytes =>
      // we assume the input is a framed stream, and encode the response(s)
      // as a framed stream as well
      bytes pipe Process.await1[BitVector] /*server.Handler.deframe*/ evalMap { bs =>
        Server.handle(this)(bs)(monitoring)
      }
    }

  /**
    * start a netty server listening to the given address
    * 
    * @param addr the address to bind to
    * @param strategy the strategy used for processing incoming requests
    * @param numBossThreads number of boss threads to create. These are
    * threads which accept incomming connection requests and assign
    * connections to a worker. If unspecified, the default of 2 will be used
    * @param numWorkerThreads number of worker threads to create. If 
    * unspecified the default of 2 * number of cores will be used
    * @param capabilities, the capabilities which will be sent to the client upon connection
    */
  def serve(addr: InetSocketAddress,
            strategy: Strategy = Strategy.DefaultStrategy,
            numBossThreads: Option[Int] = None,
            numWorkerThreads: Option[Int] = None,
            monitoring: Monitoring = Monitoring.empty,
            capabilities: Capabilities = Capabilities.default): Task[Unit] =
    transport.netty.NettyServer.start(addr,
                                      serverHandler(monitoring),
                                      strategy,
                                      numBossThreads,
                                      numWorkerThreads,
                                      capabilities,
                                      monitoring)
}

object Environment {
  val empty = Environment(Codecs.empty, Values.empty)
}
