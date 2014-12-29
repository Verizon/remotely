package remotely

import java.util.concurrent.atomic.AtomicInteger
import codecs._

trait CountServerBase {
  import Codecs._

  def ping: Int => Response[Int]

  def environment: Environment = Environment(
    Codecs.empty.codec[Int],
    populateDeclarations(Values.empty)
  )

  private def populateDeclarations(env: Values): Values = env
    .declare("ping", ping)

}

class CountServer extends CountServerBase {
  implicit val intcodec = int32
  val count: AtomicInteger = new AtomicInteger(0)
  def ping: Int => Response[Int] = { _ =>
    val r = count.incrementAndGet()
    Response.delay(r)
  }
}

object CountClient {
  val ping = Remote.ref[Int => Int]("ping")
}
