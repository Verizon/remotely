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
import io.netty.channel.{Channel,ChannelFuture,ChannelHandlerContext,ChannelFutureListener}
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
    c.pipeline.addLast("clientDeframe", new ClientDeframedHandler(fromServer))
    val toFrame = toServer.map(Bits(_)) fby Process.emit(EOS)
    val writeBytes: Task[Unit] = toFrame.evalMap(write(c)).run flatMap ( _ => Task.delay(c.flush))
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

    val _ = pool.getFactory().asInstanceOf[NettyConnectionPool].workerThreadPool.shutdownGracefully()
  }
}


object NettyTransport {
  def evalCF(cf: ChannelFuture): Task[Unit] = Task.async { cb =>
    val _ = cf.addListener(new ChannelFutureListener {
                     def operationComplete(cf: ChannelFuture): Unit =
                       if(cf.isSuccess) cb(\/-(())) else cb(-\/(cf.cause))
                   })
  }

  def write(c: Channel)(frame: Framed): Task[Unit] = evalCF(c.write(frame))

  def single(host: InetSocketAddress,
             expectedSigs: Set[Signature] = Set.empty,
             workerThreads: Option[Int] = None,
             monitoring: Monitoring = Monitoring.empty): NettyTransport = {
    new NettyTransport(NettyConnectionPool.default(Process.constant(host), expectedSigs, workerThreads, monitoring))
  }
}
