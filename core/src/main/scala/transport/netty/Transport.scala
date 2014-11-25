package remotely
package transport.netty

import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.SocketChannel
import org.jboss.netty.channel.socket.nio.NioServerSocketChannel
import java.nio.ByteBuffer
import org.apache.commons.pool2.ObjectPool
import org.jboss.netty.handler.codec.frame.FrameDecoder
import scalaz.-\/
import scalaz.concurrent.Task
import scalaz.stream.{Process,async}
import scodec.bits.BitVector
import java.util.concurrent.ExecutorService


/** 
  * set of messages passed from FrameDecoder to DeframedHandler
  * probably unecessary, I'm probably just trying to sweep an
  * isInstanceOf test under the compiler
  */
trait Deframed
case class Bits(bv: BitVector) extends Deframed
case object EOS extends Deframed

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
  me.getMessage().asInstanceOf[Deframed] match {
      case Bits(bv) =>
      if(bv.size < 1 ) {
        println("ZERO BV")
        Thread.dumpStack
      } else
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
class ServerDeframedHandler(handler: Handler, threadPool: ExecutorService) extends SimpleChannelUpstreamHandler {

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
      val queue1 = async.unboundedQueue[BitVector](scalaz.concurrent.Strategy.Executor(threadPool))
      val queue = Some(queue1)
      val stream = queue1.dequeue

      val write: Task[Unit] = (handler(stream) pipe enframe).evalMap { b =>

        Task.delay {
          Channels.write(ctx,me.getFuture(), ChannelBuffers.wrappedBuffer(b.toByteBuffer))
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
  private def close(): Unit = {
    queue.foreach(_.close.runAsync(Function.const(())))
    queue = None
  }

  override def messageReceived(ctx: ChannelHandlerContext, me: MessageEvent): Unit = {
    me.getMessage().asInstanceOf[Deframed] match {
      case Bits(bv) =>
//        println(s"received (${bv.size}): " + bv.bytes.toArray.map("%02x".format(_)).mkString)
      if(bv.size < 1 ) {
        println("ZERO BV")
        Thread.dumpStack
      } else {
        val queue = ensureQueue(ctx, me)
        queue.enqueueOne(bv).runAsync(Function.const(()))
      }

    case EOS =>
      close()
    }
  }
}

/*
/**
  * output handler which gets a stream of BitVectors and enframes them
  */
class Enframe extends SimpleChannelDownstreamHandler {
  override def writeRequested(ctx: ChannelHandlerContext, me: MessageEvent): Unit = {
    val bv = me.getMessage.asInstanceOf[BitVector]
    ctx.getChannel().write(bv.size)
    val _ = ctx.getChannel().write(bv.toByteBuffer)
  }
}
 */
