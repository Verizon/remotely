package remotely

import java.util.concurrent.atomic.AtomicInteger
import codecs._

trait CountServerBase {
  import Codecs._

  def ping: Int => Response[Int]
  def describe: Response[List[Signature]]

  def environment: Environment = Environment(
    Codecs.empty.codec[Int],
    populateDeclarations(Values.empty)
  )

  private def populateDeclarations(env: Values): Values = env
    .declare("ping", ping)
    .declare("describe", describe)
}

class CountServer extends CountServerBase {
  implicit val intcodec = int32
  val count: AtomicInteger = new AtomicInteger(0)
  def ping: Int => Response[Int] = { _ =>
    val r = count.incrementAndGet()
    Response.delay(r)
  }

  def describe: Response[List[Signature]] = Response.now(List(Signature("describe", "describe: scala.List[Signature]", Nil, "scala.List[Signature]"),
                                                              Signature("ping", "ping: Int => Int", List("Int"), "Int")))
}

object CountClient {
  val ping = Remote.ref[Int => Int]("ping")
}
