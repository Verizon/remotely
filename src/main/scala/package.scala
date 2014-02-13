
package object srpc {
  import scala.reflect.runtime.universe.TypeTag
  import scalaz.stream.{Bytes,Process}
  import scalaz.concurrent.Task
  import scodec.bits.BitVector
  import scodec.Codec

  private val C = Codecs

  /**
   * Evaluate the given remote expression at the
   * specified endpoint, and get back the result.
   * This function is completely pure - no network
   * activity occurs until the returned `Task` is
   * run, which means that retry and/or circuit-breaking
   * logic can be added with a separate layer.
   */
  def eval[A:Codec:TypeTag](e: Endpoint)(r: Remote[A]): Task[A] = for {
    conn <- e.get
    reqBits <- C.encodeRequest(r)
    respBits <- fullyRead(conn(Process.emit(Bytes.of(reqBits.toByteArray))))
    resp <- C.liftDecode(C.responseCodec[A].decode(respBits))
    result <- resp.fold(e => Task.fail(new Exception(e)),
                        a => Task.now(a))
  } yield result

  private[srpc] def fullyRead(s: Process[Task,Bytes]): Task[BitVector] =
    s.foldMonoid
     .map(b => BitVector.view(b.toArray))
     .runLastOr(BitVector.empty)
}
