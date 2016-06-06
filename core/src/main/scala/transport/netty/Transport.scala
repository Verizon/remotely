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
import java.io.File
import io.netty.buffer.{ByteBuf,Unpooled}
import io.netty.channel._
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.apache.commons.pool2.ObjectPool
import io.netty.handler.codec.ByteToMessageDecoder
import javax.net.ssl.{TrustManagerFactory,CertPathTrustManagerParameters}
import java.security.KeyStore
import java.io.FileInputStream
import scalaz.{-\/,\/,\/-,Monoid}
import scalaz.concurrent.Task
import scalaz.stream.{Process,async}
import scodec.Err
import scodec.bits.BitVector

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
  * probably unnecessary, I'm probably just trying to sweep an
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
class Deframe extends ByteToMessageDecoder {

  // stew loves mutable state
  // this will be None in between frames.
  // this will be Some(x) when we have seen all but x bytes of the
  //   current frame.
  var remaining: Option[Int] = None

  override protected def decode(ctx: ChannelHandlerContext, // this is our network connection
                                in: ByteBuf,  // this is our input
                                out: java.util.List[Object]): Unit =  {
    remaining match {
      case None =>
        // we are expecting a frame header of a single byte which is
        // the number of bytes in the upcoming frame
        if (in.readableBytes() >= 4) {
          val rem = in.readInt()
          if(rem == 0) {
            val _ = out.add(EOS)
          } else {
            remaining = Some(rem)
          }
        }
      case Some(rem) =>
        // we are waiting for at least rem more bytes, as that is what
        // is outstanding in the current frame
        if(in.readableBytes() >= rem) {
          val bytes = new Array[Byte](rem)
          in.readBytes(bytes)
          remaining = None
          val bits = BitVector.view(bytes)
          val _ = out.add(Bits(bits))
        }
    }
  }
}

class ClientDeframedHandler(queue: async.mutable.Queue[BitVector]) extends SimpleChannelInboundHandler[Framed] {
  // there has been an error
  private def fail(message: String, ctx: ChannelHandlerContext): Unit = {
    queue.fail(new Throwable(message)).runAsync(Function.const(()))
    val _ = ctx.channel.close() // should this be disconnect? is there a difference
  }

  // we've seen the end of the input, close the queue writing to the input stream
  private def close(): Unit = {
    queue.close.runAsync(Function.const(()))
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, ee: Throwable): Unit = {
    ee.printStackTrace()
    fail(ee.getMessage, ctx)
  }

  override def channelRead0(ctx: ChannelHandlerContext, f: Framed): Unit =
    f match {
      case Bits(bv) =>
        queue.enqueueOne(bv).runAsync(Function.const(()))
      case EOS =>
        close()
    }
}

/**
  * output handler which gets a stream of BitVectors and enframes them
  */

@ChannelHandler.Sharable
object Enframe extends ChannelOutboundHandlerAdapter {
  override def write(ctx: ChannelHandlerContext, obj: Object, cp: ChannelPromise): Unit = {
    obj match {
      case Bits(bv) =>
        val byv = bv.toByteVector
        val _ = ctx.writeAndFlush(Unpooled.wrappedBuffer((codecs.int32.encode(byv.size.toInt).require ++ bv).toByteBuffer), cp)
      case EOS =>
        val _ = ctx.writeAndFlush(Unpooled.wrappedBuffer(codecs.int32.encode(0).require.toByteBuffer), cp)
      case x => throw new IllegalArgumentException("was expecting Framed, got: " + x)
    }
  }
}
