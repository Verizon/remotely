package remotely
package transport.netty

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.channel.{Channel, Channels, ChannelFactory, ChannelFuture, ChannelFutureListener,ChannelHandlerContext}
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder
import org.jboss.netty.handler.codec.frame.Delimiters
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.{-\/,\/,\/-}
import scodec.Err

object NettyConnectionPool {
  def default(hosts: Process[Task,InetSocketAddress]): GenericObjectPool[Channel] = {
    new GenericObjectPool[Channel](new NettyConnectionPool(hosts))
  }
}

case class IncompatibleServer(msg: String) extends Throwable(msg)

class NettyConnectionPool(hosts: Process[Task,InetSocketAddress]) extends BasePooledObjectFactory[Channel] {
  val cf: ChannelFactory = new NioClientSocketChannelFactory(
    Executors.newCachedThreadPool(),
    Executors.newCachedThreadPool())

  val validateCapabilities: ((Capabilities,Channel)) => Task[Channel] = {
    case (capabilties, channel) =>
      val missing = Capabilities.required -- capabilties.capabilities
      if(missing.isEmpty) {
        Task {
          val pipe = channel.getPipeline()
          pipe.removeFirst()
          pipe.addLast("enframe", Enframe)
          pipe.addLast("deframe", new Deframe)
          channel
        }
      }
      else {
        channel.close()
        Task.fail(IncompatibleServer("server missing required capabilities: " + missing))
      }
  }


  class ClientNegotiate extends DelimiterBasedFrameDecoder(1000,Delimiters.lineDelimiter():_*) {

    var cb: Throwable\/(Capabilities,Channel) => Unit = _
    val capabilities: Task[(Capabilities,Channel)] = Task.async { cb =>
      this.cb = cb
    }
    
    override def decode(ctx: ChannelHandlerContext, channel: Channel, buffer: ChannelBuffer): Object = {
      val str = new String(buffer.array(), "UTF-8")
      val r = Capabilities.parseHelloString(str).bimap(
        (e: Err) => new IllegalArgumentException(e.message),
        (cap: Capabilities) => (cap,channel)
      )
      cb(r)
      r
    }
  }

  def createTask: Task[Channel] = {
    val negotiate = new ClientNegotiate
    for {
      fut <- {
        Task.delay {
                val bootstrap = new ClientBootstrap(cf)
        bootstrap.setOption("keepAlive", true)
        bootstrap.setPipeline(Channels.pipeline(negotiate))
        bootstrap.connect(hosts.once.runLast.run.get)
      }}
      chan <- {
        Task.async[Channel] { cb =>
        fut.addListener(new ChannelFutureListener {
                          def operationComplete(cf: ChannelFuture): Unit = {
                            if(cf.isSuccess) {
                              cb(\/-(cf.getChannel))
                            } else {
                              cb(-\/(cf.getCause()))
                            }
                          }
                        })
        }
      }
      capable <- negotiate.capabilities
      channel <- validateCapabilities(capable)
    } yield(channel)
  } 

  override def create: Channel = createTask.run

  override def wrap(c: Channel): PooledObject[Channel] = new DefaultPooledObject(c)

  override def passivateObject(c: PooledObject[Channel]): Unit = {
    val _ = c.getObject().getPipeline().remove(classOf[ClientDeframedHandler])
  }
}
