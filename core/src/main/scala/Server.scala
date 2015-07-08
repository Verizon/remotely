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

import scalaz.Monad
import scalaz.stream.Process
import scalaz.concurrent.Task
import scodec.bits.BitVector
import scodec.Err
import scodec.Attempt.{Failure, Successful}
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
      Process.await(request.uncons) { case (initialRequestBits, userStreamBits) =>
        // decode the request from the environment
        val (respEncoder, ctx, r) =
          codecs.requestDecoder(env).complete.decode(initialRequestBits).map(_.value)
            .fold(e => throw new Error(e.messageWithContext), identity)
        val expected = Remote.refs(r)
        val unknown = (expected -- env.values.keySet).toList
        if (unknown.nonEmpty) {
          // fail fast if the Environment doesn't know about some referenced values
          val missing = unknown.mkString("\n")
          fail(s"[validation] server values: <" + env.values.keySet + s"> does not have referenced values:\n $missing")
        }
        else {
          // we are good to try executing the request
          val (response, isStream) = eval(env)(r)(userStreamBits)
          val resultStream = if (isStream) Process.await(response(ctx))(_.asInstanceOf[Process[Task, Any]]) else Process.await(response(ctx))(Process.emit(_))
          resultStream.observeAll(monitoring.sink(ctx, r, references = expected, startNanos)).flatMap { a =>
            val result = Successful(a)
            codecs.responseEncoder(respEncoder).encode(result).toProcess
          }
        }
      }.onFailure {
        e => codecs.responseEncoder(codecs.utf8).encode(Failure(Err(formatThrowable(e)))).toProcess
      }
    }
  }

  val P = Process

  /** Evaluate a remote expression, using the given (untyped) environment. */
  def eval(env: Environment[_])(r: Remote[Any])(userStream: Process[Task, BitVector]): (Response[Any], Boolean) = {
    def evalSub(r: Remote[Any]) = eval(env)(r)(userStream)._1
    val values = env.values.values
    import Remote._
    r match {
      case Local(a,_,_) => (Response.now(a), false)
      case LocalStream(_, _,tag) =>
        env.codecs.get(tag).map { decoder =>
          val decodedStream: Process[Task, Any] = userStream.map(bits => decoder.complete.decode(bits).map(_.value)).flatMap(_.toProcess)
          (Response.now(decodedStream), true)
        }.getOrElse((Response.fail(new Error(s"[decoding] server does not have deserializers for:\n$tag")), false))
      case Ref(name) => values.lift(name) match {
        case None => (Response.delay { sys.error("Unknown name on server: " + name) }, false)
        case Some(a) => (a(), a.isStream)
      }
      // on the server, only concern ourselves w/ tree of fully saturated calls
      case Ap1(Ref(f),a) =>
        val value = values(f)
        (eval(env)(a)(userStream)._1.flatMap{value(_)}, value.isStream)
      case Ap2(Ref(f),a,b) =>
        val value = values(f)
        (Monad[Response].tuple2(evalSub(a), evalSub(b)).flatMap{case (a,b) => value(a,b)}, value.isStream)
      case Ap3(Ref(f),a,b,c) =>
        val value = values(f)
        (Monad[Response].tuple3(evalSub(a), evalSub(b), evalSub(c)).flatMap{case (a,b,c) => value(a,b,c)}, value.isStream)
      case Ap4(Ref(f),a,b,c,d) =>
        val value = values(f)
        (Monad[Response].tuple4(evalSub(a), evalSub(b), evalSub(c), evalSub(d)).flatMap{case (a,b,c,d) => value(a,b,c,d)}, value.isStream)
      case _ => (Response.delay { sys.error("unable to interpret remote expression of form: " + r) }, false)
    }
  }

  def fail(msg: String): Process[Task, Nothing] = Process.fail(new Error(msg))

  class Error(msg: String) extends Exception(msg)

  def formatThrowable(err: Throwable): String =
    err.getMessage + "\n stack trace:\n" + err.getStackTrace.mkString("\n")
}
