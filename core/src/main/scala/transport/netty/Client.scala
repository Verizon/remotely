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

import java.net.InetSocketAddress
import org.apache.commons.pool2.impl.GenericObjectPool
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.{Channel,ChannelFuture,ChannelHandlerContext,ChannelFutureListener}
import scalaz.stream.Cause
import scalaz.{-\/,\/,\/-}
import scalaz.stream.{async,Process}
import scalaz.concurrent.Task
import scodec.bits.BitVector

class NettyTransport(val pool: GenericObjectPool[Channel]) extends Handler {
  import NettyTransport._

  def apply(toServer: Process[Task, BitVector]): Process[Task, BitVector] = {
    val c = pool.borrowObject()
    val fromServer = async.unboundedQueue[BitVector](scalaz.concurrent.Strategy.DefaultStrategy)
    c.getPipeline().addLast("clientDeframe", new ClientDeframedHandler(fromServer))
    val toFrame = toServer.map(Bits(_)) fby Process.emit(EOS)
    val writeBytes: Task[Unit] = toFrame.evalMap(write(c)).run
    val result = Process.await(writeBytes)(_ => fromServer.dequeue).onHalt {
      case Cause.End =>
        pool.returnObject(c)
        Process.Halt(Cause.End)
      case cause =>
        pool.invalidateObject(c)
        Process.Halt(cause)
    }
    result
  }

  def shutdown(): Unit = {
    pool.clear()
    pool.close()
    pool.getFactory().asInstanceOf[NettyConnectionPool].cf.releaseExternalResources()
  }
}


object NettyTransport {
  def write(c: Channel)(frame: Framed): Task[Unit] = {
    val cf = c.write(frame)
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
