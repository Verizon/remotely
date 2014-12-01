package remotely
package example.benchmark
package server
package test

import org.scalatest.matchers.{Matcher,MatchResult}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import remotely.ServerException
import remotely.transport.netty._
import remotely.{Monitoring,Response,Endpoint,codecs}, codecs._, Response.Context
import scala.collection.immutable.IndexedSeq
import scalaz.{-\/,\/-}
import scalaz.concurrent.Task
import remotely._
import java.util.concurrent._
import protocol._

class BenchmarkServerSpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with transformations {

  val addr = new java.net.InetSocketAddress("localhost", 9001)
  val server = new BenchmarkServerImpl
  val shutdown: () => Unit = server.environment.serveNetty(addr, Executors.newFixedThreadPool(8))(Monitoring.consoleLogger())

  val endpoint = Endpoint.single(NettyTransport.single(addr))

  import remotely.Remote.implicits._
  import remotely.codecs._

  override def afterAll(){
    Thread.sleep(500)
    shutdown()
  }

  behavior of "identityBig"
  it should "work" in {
    val big = Big(1)
    val res = BenchmarkClient.identityBig(toBigW(big)).runWithoutContext(endpoint).run

    fromBigW(res) should equal (big)
  }
  behavior of "identityLarge"
  it should "serialize" in {
    val small = Small(Map.empty, List("asdf"))
    val big = Large(1, Nil, "string2", Map("key" -> "value"), List(Medium(2, "string", List(small), None)), Vector(small))
    fromLargeW(toLargeW(big)) should equal (big)
  }

  it should "work" in {
    val small = Small(Map.empty, List("asdf"))
    val big = Large(1, Nil, "string2", Map("key" -> "value"), List(Medium(2, "string", List(small), None)), Vector(small))
    val res = BenchmarkClient.identityLarge(toLargeW(big)).runWithoutContext(endpoint).run

    fromLargeW(res) should equal (big)
  }
}
