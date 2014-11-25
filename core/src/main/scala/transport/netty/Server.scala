package remotely
package transport.netty 

import java.util.concurrent.Executors
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.{NioServerSocketChannel, NioServerSocketChannelFactory}
import org.jboss.netty.bootstrap.ServerBootstrap
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService

class NettyServer(handler: Handler, threadPool: ExecutorService) {

  val cf: ChannelFactory = new NioServerSocketChannelFactory(Executors.newFixedThreadPool(2),
                                                             Executors.newFixedThreadPool(4))
  /**
    * Attaches our handlers to a channel
    */
  class ChannelInitialize extends ChannelPipelineFactory {
    override def getPipeline: ChannelPipeline = {
      Channels.pipeline(new Deframe(), new ServerDeframedHandler(handler, threadPool))
    }
  }
    
  def bootstrap: ServerBootstrap =  {
    val b: ServerBootstrap = new ServerBootstrap(cf)
    b.setPipelineFactory(new ChannelInitialize)
    b.setOption("child.keepAlive", true)

    b
  }
  def shutdown(): Unit = {
    cf.releaseExternalResources()
  }
}
object NettyServer {
  def start(addr: InetSocketAddress, handler: Handler, threadPool: ExecutorService) = {
    val server = new NettyServer(handler, threadPool)
    val b = server.bootstrap
    // Bind and start to accept incoming connections.
    b.bind(addr)

    () => server.shutdown()
  }

}

