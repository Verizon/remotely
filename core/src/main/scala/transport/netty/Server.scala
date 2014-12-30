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
package transport.netty 

import java.util.concurrent.Executors
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.ServerSocketChannel
import org.jboss.netty.channel.socket.nio.{NioServerSocketChannel, NioServerSocketChannelFactory}
import org.jboss.netty.bootstrap.ServerBootstrap
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService

class NettyServer(handler: Handler, threadPool: ExecutorService, capabilities: Capabilities, M: Monitoring) {

  val cf: ChannelFactory = new NioServerSocketChannelFactory(Executors.newFixedThreadPool(2),
                                                             Executors.newFixedThreadPool(4))
  /**
    * Attaches our handlers to a channel
    */
  class PipelineInitialize extends ChannelPipelineFactory {
    override def getPipeline: ChannelPipeline = {
      Channels.pipeline(ChannelInitialize)
    }
  }

  /**
    * Sends our capabilities
    */ 
  object ChannelInitialize extends SimpleChannelUpstreamHandler {

    override def channelConnected(ctx: ChannelHandlerContext,
                                  e: ChannelStateEvent): Unit = {
      super.channelConnected(ctx,e)
      M.negotiating(Option(ctx.getChannel().getRemoteAddress()), "channel connected", None)
      val encoded = Capabilities.capabilitiesCodec.encodeValid(capabilities)
      val fut = ctx.getChannel().write(ChannelBuffers.copiedBuffer(encoded.toByteBuffer))
      fut.addListener(new ChannelFutureListener {
                        def operationComplete(cf: ChannelFuture): Unit = {
                          if(cf.isSuccess) {
                            val p = ctx.getPipeline()
                            p.removeFirst()
                            p.addLast("deframe", new Deframe())
                            p.addLast("enframe", Enframe)
                            p.addLast("deframed handler", new ServerDeframedHandler(handler, threadPool, M) )
                          } 
                        }
                      })
    }
  }

  def bootstrap: ServerBootstrap =  {
    val b: ServerBootstrap = new ServerBootstrap(cf)
//    b.setParentHandler(ChannelInitialize)
    b.setPipelineFactory(new PipelineInitialize)
    b.setOption("child.keepAlive", true)
    b
  }
  def shutdown(): Unit = {
    cf.releaseExternalResources()
  }
}
object NettyServer {
  def start(addr: InetSocketAddress, handler: Handler, threadPool: ExecutorService, capabilities: Capabilities, M: Monitoring) = {
    val server = new NettyServer(handler, threadPool, capabilities, M)
    val b = server.bootstrap
    // Bind and start to accept incoming connections.
    val channel = b.bind(addr)

    () => {
      channel.close().awaitUninterruptibly()
      server.shutdown()
    }
  }
}

