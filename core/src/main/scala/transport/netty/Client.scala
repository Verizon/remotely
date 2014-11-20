package remotely
package transport.netty

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.ObjectPool
import org.apache.commons.pool2.PoolUtils
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import scalaz.{-\/,\/-}
import scalaz.stream.{async,Process}
import scalaz.concurrent.Task
import scodec.bits.BitVector
import scodec.bits.ByteVector

object NettyConnectionPool {
  def default(hosts: Process[Task,InetSocketAddress]): ObjectPool[Channel] = {
    val pool = new GenericObjectPool[Channel](new NettyConnectionPool(hosts))
    PoolUtils.erodingPool(pool)
  }
}

class NettyConnectionPool(hosts: Process[Task,InetSocketAddress]) extends BasePooledObjectFactory[Channel] {
  val cf: ChannelFactory = new NioClientSocketChannelFactory(
    Executors.newCachedThreadPool(),
    Executors.newCachedThreadPool())

  def createTask: Task[Channel] = Task.delay {
    val bootstrap = new ClientBootstrap(cf)
    bootstrap.setOption("keepAlive", true)
    bootstrap.setPipeline(Channels.pipeline(new Deframe()))
    bootstrap.connect(hosts.once.runLast.run.get)
  } flatMap { fut =>
    Task.async { cb =>
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

  override def create: Channel = createTask.run

  override def wrap(c: Channel): PooledObject[Channel] = new DefaultPooledObject(c)

  override def passivateObject(c: PooledObject[Channel]): Unit = {
    val _ = c.getObject().getPipeline().remove(classOf[ClientDeframedHandler])
  }
}

class NettyTransport(val pool: ObjectPool[Channel]) extends Handler {
  import NettyTransport._

  def apply(toServer: Process[Task, BitVector]): Process[Task, BitVector] = {
    val c = pool.borrowObject
    val fromServer = async.unboundedQueue[BitVector]
    c.getPipeline().addLast("clientDeframe", new ClientDeframedHandler(fromServer))
    Process.await((toServer pipe enframe).evalMap(write(c)).run)(server => fromServer.dequeue)
  }
}

object NettyTransport {
  def write(c: Channel)(bv: ByteVector): Task[Unit] = {
    val cf = c.write(ChannelBuffers.wrappedBuffer(bv.toByteBuffer))
    Task.async { cb =>
      cf.addListener(new ChannelFutureListener {
                       def operationComplete(cf: ChannelFuture): Unit =
                         if(cf.isSuccess) cb(\/-(())) else cb(-\/(cf.getCause))
                     })
    }
  }

  def single(host: InetSocketAddress): NettyTransport = {
    new NettyTransport(NettyConnectionPool.default(Process.constant(host)))
  }
}
