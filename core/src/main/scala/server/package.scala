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

import akka.actor.{ActorSystem, Props}
import java.net.InetSocketAddress
import javax.net.ssl.SSLEngine

package object server {

  /**
   * Start a server at the given address, using the `Handler`
   * for processing each request. Returns a thunk that can be used
   * to terminate the server.
   */
  def start(name: String)(h: Handler, addr: InetSocketAddress, ssl: Option[() => SSLEngine] = None): () => Unit = {
    val system = ActorSystem(name)
    val actor = system.actorOf(Props(new HandlerServer(h, addr, ssl)))
    () => { system.shutdown() }
  }

}
