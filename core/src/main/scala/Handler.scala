package remotely

import scalaz.stream.Process
import scalaz.concurrent.Task
import scodec.bits.BitVector

/*

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
trait Handler extends (Process[Task,BitVector] => Process[Task,BitVector]) {
  def apply(source: Process[Task,BitVector]): Process[Task,BitVector]
}

object Handler {
  def apply(handle: Process[Task,BitVector] => Process[Task,BitVector]) = new Handler {
    def apply(source: Process[Task,BitVector]) = handle(source)
  }
}

 */
