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
import io.netty.channel._, socket.SocketChannel, nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.buffer.Unpooled
import io.netty.bootstrap.ServerBootstrap
import io.netty.handler.ssl.SslContext
import java.net.InetSocketAddress
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream.{Process,async}
import scodec.bits.BitVector
import scalaz.{-\/,\/}

private[remotely] class NettyServer(handler: Handler,
                                    strategy: Strategy,
                                    numBossThreads: Int,
                                    numWorkerThreads: Int,
                                    capabilities: Capabilities,
                                    logger: Monitoring,
                                    sslContext: Option[SslContext]) {


  val bossThreadPool = new NioEventLoopGroup(numBossThreads, namedThreadFactory("nettyBoss"))
  val workerThreadPool = new NioEventLoopGroup(numWorkerThreads, namedThreadFactory("nettyWorker"))

  val bootstrap: ServerBootstrap = new ServerBootstrap()
    .group(bossThreadPool, workerThreadPool)
    .channel(classOf[NioServerSocketChannel])
    .option[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
    .childHandler(new ChannelInitializer[SocketChannel] {
                   override def initChannel(ch: SocketChannel): Unit = {
                     val pipe = ch.pipeline()
                     // add an SSL layer first iff we were constructed with an SslContext
                     sslContext.foreach { s =>
                       logger.negotiating(None, "adding ssl", None)
                       pipe.addLast(s.newHandler(ch.alloc()))
                     }
                     // add the rest of the stack
                     val _ = pipe.addLast(ChannelInitialize)
                   }
                 })


  /**
    * once a connection is negotiated, we send our capabilities string
    * to the client, which might look something like:
    *  
    * OK: [Remotely 1.0]
    */ 
  @ChannelHandler.Sharable
  object ChannelInitialize extends ChannelInboundHandlerAdapter {
    override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
      super.channelRegistered(ctx)
      logger.negotiating(Option(ctx.channel.remoteAddress), "channel connected", None)
      val encoded = Capabilities.capabilitiesCodec.encodeValid(capabilities)
      val fut = ctx.channel.writeAndFlush(Unpooled.wrappedBuffer(encoded.toByteArray))
      logger.negotiating(Option(ctx.channel.remoteAddress), "sending capabilities", None)
      val _ = fut.addListener(new ChannelFutureListener {
                                def operationComplete(cf: ChannelFuture): Unit = {
                                  if(cf.isSuccess) {
                                    logger.negotiating(Option(ctx.channel.remoteAddress), "sent capabilities", None)
                                    val p = ctx.pipeline()
                                    p.removeLast()
                                    p.addLast("deframe", new Deframe())
                                    p.addLast("enframe", Enframe)
                                    val _ = p.addLast("deframed handler", new ServerDeframedHandler(handler, strategy, logger) )
                                  } else {
                                    logger.negotiating(Option(ctx.channel.remoteAddress), s"failed to send capabilities", Option(cf.cause))
                                    shutdown()
                                  }
                                }
                              })
    }
  }

  def shutdown(): Unit = {
    bossThreadPool.shutdownGracefully()
    val _ = workerThreadPool.shutdownGracefully()
  }
}



object NettyServer {
  /**
    * start a netty server listening to the given address
    * 
    * @param addr the address to bind to
    * @param handler the request handler
    * @param strategy the strategy used for processing incoming requests
    * @param numBossThreads number of boss threads to create. These are
    * threads which accept incomming connection requests and assign
    * connections to a worker. If unspecified, the default of 2 will be used
    * @param numWorkerThreads number of worker threads to create. If 
    * unspecified the default of 2 * number of cores will be used
    * @param capabilities, the capabilities which will be sent to the client upon connection
    */
  def start(addr: InetSocketAddress,
            handler: Handler,
            strategy: Strategy = Strategy.DefaultStrategy,
            bossThreads: Option[Int] = None,
            workerThreads: Option[Int] = None,
            capabilities: Capabilities = Capabilities.default,
            logger: Monitoring = Monitoring.empty,
            sslParameters: Option[SslParameters] = None): Task[Task[Unit]] = {

    SslParameters.toServerContext(sslParameters) map { ssl =>
      logger.negotiating(Some(addr), s"got ssl parameters: $ssl", None)
      val numBossThreads = bossThreads getOrElse 2
      val numWorkerThreads = workerThreads getOrElse Runtime.getRuntime.availableProcessors

      val server = new NettyServer(handler, strategy, numBossThreads, numWorkerThreads, capabilities, logger, ssl)
      val b = server.bootstrap

      logger.negotiating(Some(addr), s"about to bind", None)
      val channel = b.bind(addr)
      logger.negotiating(Some(addr), s"bound", None)
      Task.delay {
        Task.async[Unit] { cb =>
          val _ = channel.addListener(new ChannelFutureListener {
                                        override def operationComplete(cf: ChannelFuture): Unit = {
                                          server.shutdown()
                                          if(cf.isSuccess) {
                                            cf.channel.close().awaitUninterruptibly()
                                          }
                                          cb(\/.right(()))
                                        }
                                      })
        }
      }.flatMap(identity)
    }
  }
}

/**
  * We take the bits coming to us, which have been partitioned for us
  * by FrameDecoder.
  *
  * every time we see a boundary, we close one stream, open a new
  * one, and setup a new outgoing stream back to the client.
  */
class ServerDeframedHandler(handler: Handler, strategy: Strategy, logger: Monitoring) extends SimpleChannelInboundHandler[Framed] {

  var queue: Option[async.mutable.Queue[BitVector]] = None

  //
  //  We've getting some bits, make sure we have a queue open if these
  //  bits are part of a new request.
  //
  //  if we don't have a queue open, we need to:
  //    - open a queue
  //    - get the queue's output stream,
  //    - apply the handler to the stream to get a response stream
  //    - evalMap that stream onto the side effecty "Context" to write
  //      bytes back to to the client (i hate the word context)
  //
  private def ensureQueue(ctx: ChannelHandlerContext): async.mutable.Queue[BitVector] = queue match {
    case None =>
      logger.negotiating(Option(ctx.channel.remoteAddress), "creating queue", None)
      val queue1 = async.unboundedQueue[BitVector](strategy)
      val queue = Some(queue1)
      val stream = queue1.dequeue

      val framed = handler(stream) map (Bits(_)) fby Process.emit(EOS)
      val write: Task[Unit] = framed.evalMap { b =>
        Task.delay {
          ctx.write(b)
        }
      }.run.flatMap { _ => Task.delay{ctx.flush(); ()} }

      write.runAsync { e =>
        e match {
          case -\/(e) => fail(s"uncaught exception in connection-processing logic: $e", ctx)
          case _ =>
        }
      }
      queue1
    case Some(q) => q
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, ee: Throwable): Unit = {
    ee.printStackTrace()
    fail(ee.getMessage, ctx)
  }

  // there has been an error
  private def fail(message: String, ctx: ChannelHandlerContext): Unit = {
    queue.foreach(_.fail(new Throwable(message)).runAsync(Function.const(())))
    val _ = ctx.channel.close() // should this be disconnect? is there a difference
  }

  // we've seen the end of the input, close the queue writing to the input stream
  private def close(ctx: ChannelHandlerContext): Unit = {
    queue.foreach(_.close.runAsync(Function.const(())))
    logger.negotiating(Option(ctx.channel.remoteAddress), "closing queue", None)
    queue = None
  }

  override def channelRead0(ctx: ChannelHandlerContext, f: Framed): Unit =
    f match {
      case Bits(bv) =>
        val queue = ensureQueue(ctx)
        queue.enqueueOne(bv).runAsync(Function.const(()))
      case EOS =>
        close(ctx)
    }
}

