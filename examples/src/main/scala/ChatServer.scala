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

import remotely.Response.Context
import remotely.transport.netty.NettyTransport

import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.async._
import codecs._

object ChatServer {

  val chatTopic = topic[String]()

  // on server, populate environment with codecs and values
  val env = Environment.empty
    .codec[String]
    .populate { _
      .declareStream("chat", (name: String, in: Process[Task, String]) => Response.async[Process[Task, String]]{
        chatTopic.publishOne(s"New user '$name' joined chat").map { _ =>

          val publish = in.onComplete(Process.eval_(chatTopic.publishOne(s"$name left the chat"))).to(chatTopic.publish)
          val subscribe: Process[Task, String] = Process.emit(s"Welcome to the chat, $name!") ++ chatTopic.subscribe
          // There is a problem here because we might drain publish which might drop a msg expected to be published.
          subscribe merge publish.drain
        }
      })
  }

  val addr = new InetSocketAddress("localhost", 9009)

  // on client - create local, typed declarations for server
  // functions you wish to call. This can be code generated
  // from `env`, since `env` has name/types for all declarations!
  import Remote.implicits._

  val chat = Remote.ref[(String, Process[Task, String]) => Process[Task, String]]("chat")
}

object ChatMain extends App {
  import ChatServer._
  import Remote.implicits._

  println(env)

  // create a server for this environment
  val server = env.serve(addr).run

  val transport = NettyTransport.single(addr).run
  val guy1 = Process("Hello", "I am not feeling well", "Bye")
  val guy2 = Process("Greetings", "What a nice day!", "ciao amigo")
  val client1 = chat("sad guy", guy1)
  val client2 = chat("happy guy", guy2)
  val loc: Endpoint = Endpoint.single(transport)
  val client1Msgs = client1.run(loc, Context.empty).run
  val client2Msgs = client2.run(loc, Context.empty).run
  // running a couple times just to see the latency improve for subsequent reqs
  try println { (client1Msgs merge client2Msgs).to(scalaz.stream.io.stdOutLines).run.run }
  finally {
    transport.shutdown.run
    server.run
  }
}
