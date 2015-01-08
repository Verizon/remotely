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
import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicInteger
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.{Channel, Channels, ChannelFactory, ChannelFuture, ChannelFutureListener,ChannelHandlerContext}
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder
import org.jboss.netty.handler.codec.frame.Delimiters
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.{-\/,\/,\/-}
import scodec.Err
import scodec.bits.BitVector

object NettyConnectionPool {
  def default(hosts: Process[Task,InetSocketAddress], expectedSigs: Set[Signature] = Set.empty, M: Monitoring = Monitoring.empty): GenericObjectPool[Channel] = {
    new GenericObjectPool[Channel](new NettyConnectionPool(hosts, expectedSigs, M))
  }
}

case class IncompatibleServer(msg: String) extends Throwable(msg)

class NettyConnectionPool(hosts: Process[Task,InetSocketAddress], expectedSigs: Set[Signature], M: Monitoring = Monitoring.consoleLogger("default")) extends BasePooledObjectFactory[Channel] {
  def namedThreadFactory(name: String) = new ThreadFactory {
    val num = new AtomicInteger(1)
    def newThread(runnable: Runnable) = new Thread(runnable, s"$name - ${num.incrementAndGet}")
  }


  val cf: ChannelFactory = new NioClientSocketChannelFactory(
    Executors.newCachedThreadPool(namedThreadFactory("nettyBoss")),
    Executors.newCachedThreadPool(namedThreadFactory("nettyWorker")))

  val validateCapabilities: ((Capabilities,Channel)) => Task[Channel] = {
    case (capabilties, channel) =>
      val missing = Capabilities.required -- capabilties.capabilities
      if(missing.isEmpty) {
        Task {
          val pipe = channel.getPipeline()
          pipe.removeLast()
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

  /**
    * This is an upstream handler that sits in the client's pipeline
    * during connection negotiation. 
    * 
    * It is put into the pipeline initially, we then make a call to
    * the server to ask for the descriptions of all the functions that
    * the server provides. when we receive the response, this handler
    * checks that all of the expectedSigs passed to the constructor
    * are present in the server response.
    * 
    * The state of this negotiation is captured by the `valid` Task,
    * which is asynchronously updated when the response is recieved
    */
  class ClientNegotiateDescription(channel: Channel,
                                   expectedSigs: Set[Signature],
                                   addr: InetSocketAddress) extends SimpleChannelUpstreamHandler {

    M.negotiating(Some(addr), "description negotiation begin", None)


    // the callback which will fulfil the valid task
    private[this] var cb: Throwable\/Channel => Unit = _

    val valid: Task[Channel] = Task.async { cb =>
      this.cb = cb
    }

    // here we accumulate bits as they arrive, we keep accumulating
    // until the handler below us signals the end of stream by
    // emitting a EOS
    private[this] var bits = BitVector.empty

    // negotiation failed. fulfil the callback negatively, and remove
    // ourselves from the pipeline
    private[this] def fail(msg: String): Unit = {
      val err = IncompatibleServer(msg)

      val pipe = channel.getPipeline()
      pipe.removeLast()
      M.negotiating(Some(addr), "description", Some(err))
      cb(\/.left(err))
    }

    // negiotiation succeeded, fulfil the callback positively, and
    // remove ourselves from the pipeline
    private[this] def success(): Unit = {
      val pipe = channel.getPipeline()
      pipe.removeLast()
      M.negotiating(Some(addr), "description", None)
      cb(\/.right(channel))
    }

    override def messageReceived(ctx: ChannelHandlerContext, me: MessageEvent): Unit = {
      me.getMessage().asInstanceOf[Framed] match {
        case Bits(bv) =>
          bits = bits ++ bv
        case EOS =>
          M.negotiating(Some(addr), "got end of description response", None)
          val run: Task[Unit] = for {
            resp <- codecs.liftDecode(codecs.responseDecoder[List[Signature]](codecs.list(Signature.signatureCodec)).decode(bits))
          }  yield resp.fold(e => fail(s"error processing description response: $e"),
                             serverSigs => {
                               val missing = (expectedSigs -- serverSigs)
                               if(missing.isEmpty) {
                                 success()
                               } else {
                                 fail(s"server is missing required signatures: ${missing.map(_.tag)}}")
                               }
                             })
          run.runAsync {
            case -\/(e) => M.negotiating(Some(addr), "error processing description", Some(e))
            case \/-(_) => M.negotiating(Some(addr), "finmshed processing response", None)
          }
      }
    }

  
    private[this] val pipe = channel.getPipeline()
    pipe.addLast("negotiateDescription", this)

    //
    // and then they did some investigation and realized
    // <gasp>
    // THE CALL WAS COMING FROM INSIDE THE HOUSE
    /**
      * a Task which actually makes a request to the server for the
      * description of server supported functions
      */
    private[this] val requestDescription: Task[Unit] = for {
      bits <- codecs.encodeRequest(Remote.ref[List[Signature]]("describe")).apply(Response.Context.empty)
      _ <- NettyTransport.evalCF(channel.write(Bits(bits)))
      _ <- NettyTransport.evalCF(channel.write(EOS))
      _ = M.negotiating(Some(addr), "sending describe request", None)
    } yield ()

    // actually make the request for the description
    requestDescription.runAsync {
      case \/-(bits) => ()
      case -\/(x) => fail("error requesting server description: " + x)
    }
}

  /**
    * pipeline handler which expects the Capabilities string which the
    * server sends as soon as we connect. this will check that all the
    * required capabilities are present. if so, it will replace itself
    * with the ClientNegotiateDescription handler to do the next part
    * of the negotiation.
    */
  class ClientNegotiateCapabilities extends DelimiterBasedFrameDecoder(1000,Delimiters.lineDelimiter():_*) {

    // callback which fulfills the capabilities Task
    private[this] var cb: Throwable\/(Capabilities,Channel) => Unit = _


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

  def createTask(expectedSigs: Set[Signature]): Task[Channel] = {
    val negotiateCapable = new ClientNegotiateCapabilities
    for {
      addrMaybe <- hosts.once.runLast
      addr <- addrMaybe.fold[Task[InetSocketAddress]](Task.fail(new Exception("out of connections")))(Task.now(_))
      _ = M.negotiating(Some(addr), "address selected", None)
      fut <- {
        Task.delay {
          val bootstrap = new ClientBootstrap(cf)
          bootstrap.setOption("keepAlive", true)
          bootstrap.setPipeline(Channels.pipeline(negotiateCapable))
          bootstrap.connect(addr)
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
      _ = M.negotiating(Some(addr), "channel selected", None)
      capable <- negotiateCapable.capabilities
      _ = M.negotiating(Some(addr), "capabilities received", None)
      c1 <- validateCapabilities(capable)
      _ = M.negotiating(Some(addr), "capabilities valid", None)
      c2 <- new ClientNegotiateDescription(c1,expectedSigs, addr).valid
      _ = M.negotiating(Some(addr), "description valid", None)
    } yield(c2)
  } 

  override def create: Channel = createTask(expectedSigs).run

  override def wrap(c: Channel): PooledObject[Channel] = new DefaultPooledObject(c)

  override def passivateObject(c: PooledObject[Channel]): Unit = {
    val _ = c.getObject().getPipeline().remove(classOf[ClientDeframedHandler])
  }
}
