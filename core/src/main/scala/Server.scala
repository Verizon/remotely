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

import java.net.InetSocketAddress
import scala.concurrent.duration._
import scalaz.{\/-, \/, Monad}
import scalaz.\/.{right,left}
import scalaz.stream.{merge,nio,Process}
import scalaz.concurrent.Task
import remotely.Response.Context
import scodec.bits.{BitVector,ByteVector}
import scodec.{Attempt, DecodeResult, Encoder, Err}
import scodec.Attempt.{Failure, Successful}
import scodec.interop.scalaz._
import utils._

object Server {

  /**
   * Handle a single RPC request, given a decoding
   * environment and a values environment. This `Task` is
   * guaranteed not to fail - failures will be encoded as
   * responses, to be sent back to the client, and one can
   * use the `monitoring` argument if you wish to observe
   * these failures.
   */
  def handle(env: Environment[_])(request: Process[Task, BitVector])(monitoring: Monitoring): Process[Task,BitVector] = {
    Process.await(Task.delay(System.nanoTime)) { startNanos =>
      Process.await(request.head) { initialRequest =>
        // decode the request from the environment
        val (respEncoder, ctx, r) =
          codecs.requestDecoder(env).complete.decode(initialRequest).map(_.value)
            .fold(e => throw new Error(e.messageWithContext), identity)
        val expected = Remote.refs(r)
        val unknown = (expected -- env.values.keySet).toList
        if (unknown.nonEmpty) {
          // fail fast if the Environment doesn't know about some referenced values
          val missing = unknown.mkString("\n")
          fail(s"[validation] server values: <" + env.values.keySet + s"> does not have referenced values:\n $missing")
        }
        else // we are good to try executing the request
          eval(env.values)(r)(request.tail)(ctx).flatMap {
            a =>
              val deltaNanos = System.nanoTime - startNanos
              val delta = Duration.fromNanos(deltaNanos)
              val result = Successful(a)
              monitoring.handled(ctx, r, expected, \/-(a), delta)
              codecs.responseEncoder(respEncoder).encode(result).toProcess
          }.onFailure {
            // this is a little convoluted - we catch this exception just so
            // we can log the failure using `monitoring`, then reraise it
            e =>
              val deltaNanos = System.nanoTime - startNanos
              val delta = Duration.fromNanos(deltaNanos)
              monitoring.handled(ctx, r, expected, left(e), delta)
              Process.fail(e)
          }
      }
    }.onFailure {
      e => codecs.responseEncoder(codecs.utf8).encode(Failure(Err(formatThrowable(e)))).toProcess
    }
  }

  val P = Process

  /** Evaluate a remote expression, using the given (untyped) environment. */
  def eval[A](env: Values)(r: Remote[A])(userStream: Process[Task, Any]): Response[A] = {
    import Remote._
    r match {
      case Local(a,_,_) => Response.now(a)
      case LocalStream(_, _,_) => Response.stream(userStream.asInstanceOf[Process[Task, A]])
      case Ref(name) => env.values.lift(name) match {
        case None => Response.delay { sys.error("Unknown name on server: " + name) }
        case Some(a) => a().asInstanceOf[Response[A]]
      }
      // on the server, only concern ourselves w/ tree of fully saturated calls
      case Ap1(Ref(f),a) => eval(env)(a)(userStream).flatMap(env.values(f)(_)) .asInstanceOf[Response[A]]
      case Ap2(Ref(f),a,b) => Monad[Response].tuple2(eval(env)(a)(userStream), eval(env)(b)(userStream)).flatMap{case (a,b) => env.values(f)(a,b)} .asInstanceOf[Response[A]]
      case Ap3(Ref(f),a,b,c) => Monad[Response].tuple3(eval(env)(a)(userStream), eval(env)(b)(userStream), eval(env)(a)(userStream)).flatMap{case (a,b,c) => env.values(f)(a,b,c)} .asInstanceOf[Response[A]]
      case Ap4(Ref(f),a,b,c,d) => Monad[Response].tuple4(eval(env)(a)(userStream), eval(env)(b)(userStream), eval(env)(c)(userStream), eval(env)(d)(userStream)).flatMap{case (a,b,c,d) => env.values(f)(a,b,c,d)} .asInstanceOf[Response[A]]
      case _ => Response.delay { sys.error("unable to interpret remote expression of form: " + r) }
    }
  }

  def fail(msg: String): Process[Task, Nothing] = Process.fail(new Error(msg))

  class Error(msg: String) extends Exception(msg)

  def formatThrowable(err: Throwable): String =
    err.getMessage + "\n stack trace:\n" + err.getStackTrace.mkString("\n")
}
