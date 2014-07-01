package remotely

import scala.concurrent.duration.Duration
import intelmedia.ws.funnel.instruments._
import scalaz.\/

class FunnelMonitoring extends Monitoring {
  private val roundtrip = timer("remotely/roundtrip")

  def handled[A](
    ctx: Response.Context,
    req: Remote[A],
    references: Iterable[String],
    result: Throwable \/ A,
    took: Duration): Unit = roundtrip.record(took)

}
