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
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.SocketChannel
import org.jboss.netty.channel.socket.nio.NioServerSocketChannel
import java.nio.ByteBuffer
import org.apache.commons.pool2.ObjectPool
import org.jboss.netty.handler.codec.frame.FrameDecoder
import scalaz.{-\/,\/,\/-}
import scalaz.concurrent.Task
import scalaz.stream.{Process,async}
import scodec.Err
import scodec.bits.BitVector
import java.util.concurrent.ExecutorService
import scodec.bits.ByteVector

///////////////////////////////////////////////////////////////////////////
// A Netty based transport for remotely.
//
// Netty connections consist of two pipelines on each side of a
// network connection, an outbound pipeline and an inbound pipeline
//
// Client Outbound Pipeline
//
//               +---------+
// [ Network ] ← | Enframe | ←  Client request
//               +---------+
//
// Server Inbound Pipeline
//
//               +---------+   +-----------------------+
// [ Network ] → | Deframe | → | ServerDeframedHandler |
//               +---------+   +-----------------------+
//
// Deframe - decodes the framing in order to find message boundaries
//
// ServerDeframedHandler - accepts full messages from Deframe, for
// each message, opens a queue/processs pair, calls the handler which
// returns a result Process which is copied back out to the network
//
// Server Outbound Pipeline
//
//               +---------+   +-----------------------+
// [ Network ] ← | Enframe | ← | ServerDeframedHandler |
//               +---------+   +-----------------------+
//
// Enfrome - prepends each ByteVector emitted from the Process with a
// int indicating how many bytes are in this ByteVector, when the
// Process halts, a zero is written indicating the end of the stream
//
//               +-----------------------+
// [ Network ] ← | ServerDeframedHandler |
//               +-----------------------+
//
//
// Client Intbound Pipeline
//
//               +---------+   +-----------------------+
// [ Network ] → | Deframe | → | ClientDeframedHandler |
//               +---------+   +-----------------------+
//
// Deframe - The same as in the Server pipeline
//
// ClientDeframedHandler - This is added to the pipeline when a
// connection is borrowed from the connection pool. It holds onto a
// queue which it feeds with frames passed up from Deframe. This queue
// feeds the Process which represents the output of a remote call.
//

/**
  * set of messages passed in and out of the FrameEncoder/FrameDecoder
  * probably unecessary, I'm probably just trying to sweep an
  * isInstanceOf test under the compiler
  */
sealed trait Framed
case class Bits(bv: BitVector) extends Framed
case object EOS extends Framed

/**
  * handler which is at the lowest level of the stack, it decodes the
  * frames as described (where STU? where are they described?)  it
  * emits Deframed things to the next level up which can then treat
  * the streams between each EOS we emit as a separate request
  */
class Deframe extends FrameDecoder {

  // stew loves mutable state
  // this will be None in between frames.
  // this will be Some(x) when we have seen all but x bytes of the
  //   current frame.
  var remaining: Option[Int] = None

  override protected def decode(ctx: ChannelHandlerContext, // this is our network connection
                                out: Channel,
                                in: ChannelBuffer  // this is our input
                               ): Object =  {
    remaining match {
      case None =>
        // we are expecting a frame header of a single byte which is
        // the number of bytes in the upcoming frame
        if (in.readableBytes() >= 4) {
          val rem = in.readInt()
          if(rem == 0) {
            EOS
          } else {
            remaining = Some(rem)
            null
          }
        } else {
          null
        }
      case Some(rem) =>
        // we are waiting for at least rem more bytes, as that is what
        // is outstanding in the current frame
        if(in.readableBytes() < rem) {
          null
        } else {
          val bytes = new Array[Byte](rem)
          in.readBytes(bytes)
          remaining = None
          val bits = BitVector.view(bytes)
          Bits(bits)
        }
    }
  }
}

class ClientDeframedHandler(queue: async.mutable.Queue[BitVector]) extends SimpleChannelUpstreamHandler {
  // there has been an error
  private def fail(message: String, ctx: ChannelHandlerContext): Unit = {
    queue.fail(new Throwable(message)).runAsync(Function.const(()))
    val _ = ctx.getChannel.close() // should this be disconnect? is there a difference
  }

  // we've seen the end of the input, close the queue writing to the input stream
  private def close(): Unit = {
    queue.close.runAsync(Function.const(()))
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, ee: ExceptionEvent): Unit = {
    ee.getCause.printStackTrace()
    fail(ee.getCause().getMessage, ctx)
  }

  override def messageReceived(ctx: ChannelHandlerContext, me: MessageEvent): Unit = {
  me.getMessage().asInstanceOf[Framed] match {
    case Bits(bv) =>
      queue.enqueueOne(bv).runAsync(Function.const(()))
    case EOS =>
      close()
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
class ServerDeframedHandler(handler: Handler, threadPool: ExecutorService, M: Monitoring) extends SimpleChannelUpstreamHandler {

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
  private def ensureQueue(ctx: ChannelHandlerContext, me: MessageEvent): async.mutable.Queue[BitVector] = queue match {
    case None =>
      M.negotiating(Option(ctx.getChannel().getRemoteAddress()), "creating queue", None)
      val queue1 = async.unboundedQueue[BitVector](scalaz.concurrent.Strategy.Executor(threadPool))
      val queue = Some(queue1)
      val stream = queue1.dequeue

      val framed = handler(stream) map (Bits(_)) fby Process.emit(EOS)
      val write: Task[Unit] = framed.evalMap { b =>
        Task.delay {
          Channels.write(ctx,me.getFuture(), b)
        }
      }.run

      write.runAsync { e =>
        e match {
          case -\/(e) => fail(s"uncaught exception in connection-processing logic: $e", ctx)
          case _ =>
        }
      }
      queue1
    case Some(q) => q
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, ee: ExceptionEvent): Unit = {
    ee.getCause.printStackTrace()
    fail(ee.getCause().getMessage, ctx)
  }

  // there has been an error
  private def fail(message: String, ctx: ChannelHandlerContext): Unit = {
    queue.foreach(_.fail(new Throwable(message)).runAsync(Function.const(())))
    val _ = ctx.getChannel.close() // should this be disconnect? is there a difference
  }

  // we've seen the end of the input, close the queue writing to the input stream
  private def close(ctx: ChannelHandlerContext): Unit = {
    queue.foreach(_.close.runAsync(Function.const(())))
    M.negotiating(Option(ctx.getChannel().getRemoteAddress()), "closing queue", None)
    queue = None
  }

  override def messageReceived(ctx: ChannelHandlerContext, me: MessageEvent): Unit = {
    me.getMessage().asInstanceOf[Framed] match {
      case Bits(bv) =>
        val queue = ensureQueue(ctx, me)
        queue.enqueueOne(bv).runAsync(Function.const(()))
    case EOS =>
        close(ctx)
    }
  }
}

/**
  * output handler which gets a stream of BitVectors and enframes them
  */
object Enframe extends SimpleChannelDownstreamHandler {
  override def writeRequested(ctx: ChannelHandlerContext, me: MessageEvent): Unit = {
    me.getMessage match {
      case Bits(bv) =>
        val byv = bv.toByteVector
        Channels.write(ctx, me.getFuture(), ChannelBuffers.copiedBuffer(codecs.int32.encodeValid(byv.size).toByteBuffer))
        Channels.write(ctx, me.getFuture(), ChannelBuffers.copiedBuffer(bv.toByteBuffer))
      case EOS =>
        Channels.write(ctx, me.getFuture(), ChannelBuffers.copiedBuffer(codecs.int32.encodeValid(0).toByteBuffer))
      case x => throw new IllegalArgumentException("was expecting Framed, got: " + x)
    }
  }
}
