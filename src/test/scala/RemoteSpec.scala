package remotely

import java.net.InetSocketAddress
import org.scalacheck._
import Prop._
import scalaz.concurrent.Task

object RemoteSpec extends Properties("Remote") {

  import codecs._
  import Remote.implicits._

  val env = Environment.empty
    .codec[Int]
    .codec[Double]
    .codec[List[Int]]
    .declare("sum") { (d: List[Int]) => d.sum }
    .declare("sum") { (d: List[Double]) => d.sum }

  implicit val clientPool = akka.actor.ActorSystem("rpc-client")
  val addr = new InetSocketAddress("localhost", 8080)
  val server = Server.start(env)(addr)(Monitoring.empty)
  val loc: Endpoint = Endpoint.single(addr) // takes ActorSystem implicitly

  val sum = Remote.ref[List[Int] => Int]("sum")

  property("roundtrip") =
    forAll { (l: List[Int]) => l.sum == sum(l).run(loc).run }

  property("check-serializers") = secure {
    // verify that server returns a meaningful error when it asks for
    // decoder(s) the server does not know about
    val wrongsum = Remote.ref[List[Float] => Float]("sum")
    val t: Task[Float] = wrongsum(List(1.0f, 2.0f, 3.0f)).run(loc)
    t.attemptRun.fold(
      e => {
        println("test resulted in error, as expected:")
        println(prettyError(e.toString))
        true
      },
      a => false
    )
  }

  property("check-declarations") = secure {
    // verify that server returns a meaningful error when client asks
    // for a remote ref that is unknown
    val wrongsum = Remote.ref[List[Int] => Int]("product")
    val t: Task[Int] = wrongsum(List(1, 2, 3)).run(loc)
    t.attemptRun.fold(
      e => {
        println("test resulted in error, as expected:")
        println(prettyError(e.toString))
        true
      },
      a => false
    )
  }

  property("encoding speed") = {
    val N = 2000
    val M = 1024
    val ints = List.range(0, M)
    val c = scodec.Codec[List[Int]]
    val t = time {
      (0 until N).foreach { _ => c.encode(ints); () }
    }
    println { "took " + t / 1000.0 +"s to encode " + (M*N*4 / 1e6) + " MB" }
    true
  }

  property("decoding speed") = {
    val N = 2000
    val M = 1024
    val ints = List.range(0, M)
    val c = scodec.Codec[List[Int]]
    val bits = c.encodeValid(ints)
    val t = time {
      (0 until N).foreach { _ => c.decode(bits); () }
    }
    println { "took " + t / 1000.0 +"s to decode " + (M*N*4 / 1e6) + " MB" }
    true
  }

  property("round trip speed") = {
    val l: List[Int] = List(1)
    val N = 5000
    val t = time {
      (0 until N).foreach { _ => sum(l).run(loc).run; () }
    }
    println { "round trip took average of: " + (t/N.toDouble) + " milliseconds" }
    true
  }

  // NB: this property should always appear last, so it runs after all properties have run
  property("cleanup") = lazily {
    server()
    clientPool.shutdown()
    true
  }

  def time(a: => Unit): Long = {
    val start = System.currentTimeMillis
    a
    System.currentTimeMillis - start
  }

  def prettyError(msg: String): String = {
    msg.take(msg.indexOfSlice("stack trace:"))
  }

  def lazily(p: => Prop): Prop = {
    lazy val pe = secure { p }
    new Prop { def apply(p: Gen.Parameters) = pe(p) }
  }

}
