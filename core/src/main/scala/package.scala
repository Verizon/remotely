
package object remotely {
  import scala.concurrent.duration._
  import scala.reflect.runtime.universe.TypeTag
  import scalaz.stream.{Process,Process1}
  import scalaz.concurrent.Task
  import scalaz.\/.{left,right}
  import scalaz.Monoid
  import scodec.bits.{BitVector,ByteVector}
  import scodec.Decoder


  /**
   * Evaluate the given remote expression at the
   * specified endpoint, and get back the result.
   * This function is completely pure - no network
   * activity occurs until the returned `Response` is
   * run.
   *
   * The `Monitoring` instance is notified of each request.
   */
  def evaluate[A:Decoder:TypeTag](e: Endpoint, M: Monitoring = Monitoring.empty)(r: Remote[A]): Response[A] =
  Remote.localize(r).flatMap { r => Response.scope { Response { ctx => // push a fresh ID onto the call stack
    val refs = Remote.refs(r)
    def reportErrors[R](startNanos: Long)(t: Task[R]): Task[R] =
      t.attempt.flatMap { _.fold(
        { err =>
          M.handled(ctx, r, refs, left(err), Duration.fromNanos(System.nanoTime - startNanos))
          Task.fail(err)
        },
        Task.now
      )}
    Task.delay { System.nanoTime } flatMap { start =>
      for {
        reqBits <- codecs.encodeRequest(r).apply(ctx)
        respBytes <- reportErrors(start) {
          val reqBytestream = Process.emit(reqBits)
          fullyRead(e(reqBytestream).pipe(Process.await1[BitVector]))
        }
        resp <- reportErrors(start) { codecs.liftDecode(codecs.responseDecoder[A].decode(respBytes)) }
        result <- resp.fold(
          { e =>
            val ex = ServerException(e)
            val delta = System.nanoTime - start
            M.handled(ctx, r, Remote.refs(r), left(ex), Duration.fromNanos(delta))
            Task.fail(ex)
          },
          { a =>
            val delta = System.nanoTime - start
            M.handled(ctx, r, refs, right(a), Duration.fromNanos(delta))
            Task.now(a)
          }
        )
      } yield result
    }
  }}}

  implicit val BitVectorMonoid = Monoid.instance[BitVector]((a,b) => a ++ b, BitVector.empty)
  implicit val ByteVectorMonoid = Monoid.instance[ByteVector]((a,b) => a ++ b, ByteVector.empty)

  private[remotely] def fullyRead(s: Process[Task,BitVector]): Task[BitVector] =
    s.runFoldMap(identity)

  private[remotely] def enframe: Process1[BitVector,ByteVector] = {
    Process.await1[BitVector].map { bs =>
      val bytes = bs.bytes
      codecs.int32.encodeValid(bytes.size).bytes ++ bytes
    } fby Process.emit(codecs.int32.encodeValid(0).bytes)
  }
}
