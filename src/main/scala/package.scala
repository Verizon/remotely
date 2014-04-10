
package object remotely {
  import scala.concurrent.duration._
  import scala.reflect.runtime.universe.TypeTag
  import scalaz.stream.{Bytes,Process}
  import scalaz.concurrent.Task
  import scalaz.\/.{left,right}
  import scalaz.Monoid
  import scodec.bits.{BitVector,ByteVector}
  import scodec.Decoder
  import remotely.server.Handler

  /**
   * Evaluate the given remote expression at the
   * specified endpoint, and get back the result.
   * This function is completely pure - no network
   * activity occurs until the returned `Task` is
   * run, which means that retry and/or circuit-breaking
   * logic can be added with a separate layer.
   *
   * The `Monitoring` instance is notified of each request.
   */
  def evaluate[A:Decoder:TypeTag](e: Endpoint, M: Monitoring = Monitoring.empty)(r: Remote[A]): Response[A] =
  Response.scope { Response { ctx => // push a fresh ID onto the call stack
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
        conn <- e.get
        reqBits <- codecs.encodeRequest(r)(ctx)
        respBytes <- reportErrors(start) {
          val reqBytestream = Process.emit(reqBits.toByteVector).pipe(Handler.frame)
          fullyRead(conn(reqBytestream).pipe(Handler.frames)) // we assume the server response is a framed stream
        }
        resp <- reportErrors(start) { codecs.liftDecode(codecs.responseDecoder[A].decode(respBytes.toBitVector)) }
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
  }}

  implicit val BitVectorMonoid = Monoid.instance[BitVector]((a,b) => a ++ b, BitVector.empty)
  implicit val ByteVectorMonoid = Monoid.instance[ByteVector]((a,b) => a ++ b, ByteVector.empty)

  private[remotely] def fullyRead(s: Process[Task,ByteVector]): Task[ByteVector] =
    s.runFoldMap(identity)
}
