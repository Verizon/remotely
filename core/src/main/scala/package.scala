import remotely.Remote.LocalStream

import scalaz.{\/-, -\/, \/}

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


package object remotely {
  import scala.concurrent.duration._
  import scala.reflect.runtime.universe.TypeTag
  import scalaz.stream.Process
  import scalaz.concurrent.Task
  import scalaz.\/.{left,right}
  import scalaz.Monoid
  import scodec.bits.{BitVector,ByteVector}
//  import scodec.Decoder
  import utils._

/**
  * Represents the logic of a connection handler, a function
  * from a stream of bytes to a stream of bytes, which will
  * be sent back to the client. The connection will be closed
  * when the returned stream terminates.
  *
  * NB: This type cannot represent certain kinds of 'coroutine'
  * client/server interactions, where the server awaits a response
  * to a particular packet sent before continuing.
  */
  type Handler = Process[Task,BitVector] => Process[Task,BitVector]

  /**
   * Evaluate the given remote stream at the
   * specified endpoint, and get back the result.
   * This function is completely pure - no network
   * activity occurs until the returned `Response` is
   * run.
   *
   * The `Monitoring` instance is notified of each request.
   */
  def evaluateStream[A:scodec.Codec:TypeTag](e: Endpoint, M: Monitoring = Monitoring.empty)(r: Remote[Process[Task, A]]): Response[Process[Task,A]] =
    evaluateImpl(e,M,true)(r)(Remote.toTag[A]).asInstanceOf[Response[Process[Task, A]]]

  /**
   * Evaluate the given remote expression at the
   * specified endpoint, and get back the result.
   * This function is completely pure - no network
   * activity occurs until the returned `Response` is
   * run.
   *
   * The `Monitoring` instance is notified of each request.
   */
  def evaluate[A:scodec.Codec:TypeTag](e: Endpoint, M: Monitoring = Monitoring.empty)(r: Remote[A]): Response[A] =
    evaluateImpl(e,M,false)(r)(Remote.toTag[A]).asInstanceOf[Response[A]]

  /**
   * In the actual implementation, we don't really care what type of Remote we receive. In any case, it is
   * going to become a stream of bits and produce wtv is the expected result unless there is a decoding error.
   */
  def evaluateImpl[A:scodec.Codec:TypeTag, B](e: Endpoint, M: Monitoring = Monitoring.empty, isStream: Boolean)(r: Remote[B])(remoteTag: String): Response[B] = {
    Response.scope { Response { ctx =>
      val refs = Remote.refs(r)

      val stream = (r collect { case l: LocalStream[Any]@unchecked => l }).headOption
      val userBits = codecs.encodeLocalStream(stream)

      def reportErrors[R](startNanos: Long)(t: Process[Task, R]): Process[Task, R] =
        t.onFailure { e =>
          M.handled(ctx, r, refs, left(e), Duration.fromNanos(System.nanoTime - startNanos))
          Process.fail(e): Process[Task, R]
        }

      def failOnServerSideErr[A](startNanos: Long)(p: Process[Task, String \/ A]): Process[Task, A] =
        p.flatMap {
          case -\/(error: String) =>
            val ex = ServerException(error)
            val delta = System.nanoTime - startNanos
            M.handled(ctx, r, Remote.refs(r), left(ex), Duration.fromNanos(delta))
            Process.fail(ex)
          case \/-(a) =>
            val delta = System.nanoTime - startNanos
            M.handled(ctx, r, refs, right(a), Duration.fromNanos(delta))
            Process.emit(a)
        }

      val timeAndConnection = for {
        start <- Task.delay(System.nanoTime())
        conn <- e.get
      } yield (start, conn)

      val resultStream = Process.await(timeAndConnection) { case (start, conn) =>
        val reqBits = codecs.encodeRequest(r, ctx, remoteTag).toProcess
        val respBits = reportErrors(start) {
          val allBits = reqBits ++ userBits
          conn(allBits)
        }
        reportErrors(start) {
          respBits.map(bits =>
            failOnServerSideErr(start)(codecs.responseDecoder[A].complete.decode(bits).map(_.value).toProcess)
          ).flatten
        }
      }
      // We assume that if it's not conceptually a stream, we encoded it on the server as a one element stream
      (if (isStream) Task.now(resultStream) else resultStream.runLast.map(_.get)).asInstanceOf[Task[B]]
    }}
  }

  implicit val BitVectorMonoid = Monoid.instance[BitVector]((a,b) => a ++ b, BitVector.empty)
  implicit val ByteVectorMonoid = Monoid.instance[ByteVector]((a,b) => a ++ b, ByteVector.empty)

}
