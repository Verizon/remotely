package remotely
package transport.netty 

import java.util.concurrent.Executors
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.{NioServerSocketChannel, NioServerSocketChannelFactory}
import org.jboss.netty.bootstrap.ServerBootstrap
import java.net.InetSocketAddress

class NettyServer(handler: Handler) {

  val cf: ChannelFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                                                             Executors.newCachedThreadPool())
  /**
    * Attaches our handlers to a channel
    */
  class ChannelInitialize extends ChannelPipelineFactory {
    override def getPipeline: ChannelPipeline = {
      Channels.pipeline(new Deframe(), new ServerDeframedHandler(handler))
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
  def start(addr: InetSocketAddress, handler: Handler) = {
    val server = new NettyServer(handler)
    val b = server.bootstrap
    // Bind and start to accept incoming connections.
    b.bind(addr)

    () => server.shutdown()
  }

}

