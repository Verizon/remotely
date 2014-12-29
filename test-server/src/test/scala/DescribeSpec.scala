package remotely
package test

import org.scalatest.matchers.{Matcher,MatchResult}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scodec.Codec
import transport.netty._
import java.util.concurrent.Executors

class DescribeTestServerImpl extends DescribeTestServer {
  override def foo = Response.delay(Foo(1))
  override def fooId = (foo: Foo) => Response.now(foo)
  override def foobar = (foo: Foo) => Response.now(Bar(foo.a))
}

class DescribeSpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {



  val addr = new java.net.InetSocketAddress("localhost", 9006)
  val server = new DescribeTestServerImpl
  val shutdown: () => Unit = server.environment.serveNetty(addr,
                                                           Executors.newCachedThreadPool,
                                                           Monitoring.empty)

  val endpoint = Endpoint.single(NettyTransport.single(addr))
  
  behavior of "Describe"
  
  it should "work" in {
    import codecs.list
    import Signature._
    import remotely.Remote.implicits._
    val desc = evaluate(endpoint, Monitoring.consoleLogger())(DescribeTestClient.describe).apply(Response.Context.empty).run
    desc should contain (Signature("foo", "foo: remotely.test.Foo", Nil, "remotely.test.Foo"))
    desc should contain (Signature("fooId", "fooId: remotely.test.Foo => remotely.test.Foo", List("remotely.test.Foo"), "remotely.test.Foo"))
    desc should contain (Signature("foobar", "foobar: remotely.test.Foo => remotely.test.Bar", List("remotely.test.Foo"), "remotely.test.Bar"))
    desc should contain (Signature("describe", "describe: List[remotely.Signature]", Nil, "List[Remotely.Signature]"))
  }

  override def afterAll() {
    shutdown()
  }
}

