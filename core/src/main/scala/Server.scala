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
import scalaz.{\/,Monad}
import scalaz.\/.{right,left}
import scalaz.stream.{merge,nio,Process}
import scalaz.concurrent.Task
import remotely.Response.Context
import scodec.bits.{BitVector,ByteVector}
import scodec.{Attempt, DecodeResult, Encoder, Err}
import scodec.Attempt.Successful
import scodec.interop.scalaz._

object Server {

  /**
   * Handle a single RPC request, given a decoding
   * environment and a values environment. This `Task` is
   * guaranteed not to fail - failures will be encoded as
   * responses, to be sent back to the client, and one can
   * use the `monitoring` argument if you wish to observe
   * these failures.
   */
  def handle(env: Environment)(request: BitVector)(monitoring: Monitoring): Task[BitVector] = {

    Task.delay(System.nanoTime).flatMap { startNanos => Task.suspend {
      // decode the request from the environment

      val DecodeResult((respEncoder,ctx,r), trailing) =
        codecs.requestDecoder(env).decode(request).
          fold(e => throw new Error(e.messageWithContext), identity)

      val expected = Remote.refs(r)
      val unknown = (expected -- env.values.keySet).toList
      if (unknown.nonEmpty) { // fail fast if the Environment doesn't know about some referenced values
        val missing = unknown.mkString("\n")
        fail(s"[validation] server values: <" + env.values.keySet + s"> does not have referenced values:\n $missing")
      } else if (trailing.nonEmpty) // also fail fast if the request has trailing bits (usually a codec error)
        fail(s"[validation] trailing bytes in request: ${trailing.toByteVector}")
      else // we are good to try executing the request
        eval(env.values)(r)(ctx).flatMap {
          a =>
          val deltaNanos = System.nanoTime - startNanos
          val delta = Duration.fromNanos(deltaNanos)
          val result = right(a)
          monitoring.handled(ctx, r, expected, result, delta)
          toTask(codecs.responseEncoder(respEncoder).encode(Successful(a)))
        }.attempt.flatMap {
          // this is a little convoluted - we catch this exception just so
          // we can log the failure using `monitoring`, then reraise it
          _.fold(
            e => {
              val deltaNanos = System.nanoTime - startNanos
              val delta = Duration.fromNanos(deltaNanos)
              monitoring.handled(ctx, r, expected, left(e), delta)
              Task.fail(e)
            },
            bits => Task.now(bits)
          )
        }
    }}.attempt.flatMap { _.fold(
      e => toTask(codecs.responseEncoder(codecs.utf8).encode(Attempt.failure(Err(formatThrowable(e))))),
      bits => Task.now(bits)
                        )}
  }

  val P = Process

  /** Evaluate a remote expression, using the given (untyped) environment. */
  def eval[A](env: Values)(r: Remote[A]): Response[A] = {
    import Remote._
    r match {
      case Local(a,_,_) => Response.now(a)
      case Ref(name) => env.values.lift(name) match {
        case None => Response.delay { sys.error("Unknown name on server: " + name) }
        case Some(a) => a().asInstanceOf[Response[A]]
      }
      // on the server, only concern ourselves w/ tree of fully saturated calls
      case Ap1(Ref(f),a) => eval(env)(a).flatMap(env.values(f)(_)) .asInstanceOf[Response[A]]
      case Ap2(Ref(f),a,b) => Monad[Response].tuple2(eval(env)(a), eval(env)(b)).flatMap{case (a,b) => env.values(f)(a,b)} .asInstanceOf[Response[A]]
      case Ap3(Ref(f),a,b,c) => Monad[Response].tuple3(eval(env)(a), eval(env)(b), eval(env)(c)).flatMap{case (a,b,c) => env.values(f)(a,b,c)} .asInstanceOf[Response[A]]
      case Ap4(Ref(f),a,b,c,d) => Monad[Response].tuple4(eval(env)(a), eval(env)(b), eval(env)(c), eval(env)(d)).flatMap{case (a,b,c,d) => env.values(f)(a,b,c,d)} .asInstanceOf[Response[A]]
      case Ap5(Ref(f),a,b,c,d,e) => Monad[Response].tuple5(eval(env)(a), eval(env)(b), eval(env)(c), eval(env)(d), eval(env)(e)).flatMap{case (a,b,c,d,e) => env.values(f)(a,b,c,d,e)} .asInstanceOf[Response[A]]
      case _ => Response.delay { sys.error("unable to interpret remote expression of form: " + r) }
    }
  }

  private def toTask[A](att: Attempt[A]): Task[A] =
    att.fold(e => Task.fail(new Error(e.messageWithContext)),
             a => Task.now(a))

  def fail(msg: String): Task[Nothing] = Task.fail(new Error(msg))

  class Error(msg: String) extends Exception(msg)

  def formatThrowable(err: Throwable): String =
    err.getMessage + "\n stack trace:\n" + err.getStackTrace.mkString("\n")
}
