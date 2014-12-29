package remotely

import java.util.concurrent.Executors
import org.scalatest.matchers.{Matcher,MatchResult}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import remotely.transport.netty._
import scala.concurrent.duration.DurationInt
import scalaz.stream.Process
import codecs._

class CapabilitiesSpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {
  
  val addr1 = new java.net.InetSocketAddress("localhost", 9003)

  val server1 = new CountServer
  val shutdown1: () => Unit = server1.environment.serveNetty(addr1, Executors.newCachedThreadPool, Monitoring.empty, Capabilities(Set()))

  override def afterAll() {
    shutdown1()
  }
  val endpoint1 = Endpoint.single(NettyTransport.single(addr1))

  behavior of "Capabilities"
  
  it should "not call an incompatible server" in {
    import Response.Context
    import Remote.implicits._
    import codecs._

    an[IncompatibleServer] should be thrownBy {
      evaluate(endpoint1, Monitoring.empty)(CountClient.ping(1)).apply(Context.empty).run
    }
  }
}

