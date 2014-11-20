package remotely

import java.net.InetSocketAddress
import org.scalacheck._
import Prop._
import scalaz.concurrent.Task
import transport.akka._

object RemoteSpec extends Properties("Remote") {
  import codecs._
  import Remote.implicits._

  val env = Environment.empty
    .codec[Int]
    .codec[Double]
    .codec[List[Int]]
    .codec[List[Double]]
    .populate { _
      .declareStrict("sum", (d: List[Int]) => d.sum)
      .declare("sum", (d: List[Double]) => Response.now(d.sum))
      .declare("add1", (d: List[Int]) => Response.now(d.map(_ + 1):List[Int]))
    }

  implicit val clientPool = akka.actor.ActorSystem("rpc-client")
  val addr = new InetSocketAddress("localhost", 8080)
  val server = env.serveNetty(addr)(Monitoring.empty)
  val akkaTrans = AkkaTransport.single(clientPool,addr)
  val loc: Endpoint = Endpoint.single(akkaTrans)

  val sum = Remote.ref[List[Int] => Int]("sum")
  val sumD = Remote.ref[List[Double] => Double]("sum")
  val mapI = Remote.ref[List[Int] => List[Int]]("add1")

  val ctx = Response.Context.empty

  property("roundtrip") =
    forAll { (l: List[Int], kvs: Map[String,String]) =>
      l.sum == sum(l).runWithContext(loc, ctx ++ kvs).run
    }

  property("roundtrip[Double]") =
    forAll { (l: List[Double], kvs: Map[String,String]) =>
      l.sum == sumD(l).runWithContext(loc, ctx ++ kvs).run
    }

  property("roundtrip[List[Int]]") =
    forAll { (l: List[Int], kvs: Map[String,String]) =>
      l.map(_ + 1) == mapI(l).runWithContext(loc, ctx ++ kvs).run
    }

  property("check-serializers") = secure {
    // verify that server returns a meaningful error when it asks for
    // decoder(s) the server does not know about
    val wrongsum = Remote.ref[List[Float] => Float]("sum")
    val t: Task[Float] = wrongsum(List(1.0f, 2.0f, 3.0f)).runWithContext(loc, ctx)
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
    val t: Task[Int] = wrongsum(List(1, 2, 3)).runWithContext(loc, ctx)
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
      (0 until N).foreach { _ => sum(l).runWithContext(loc, ctx).run; () }
    }
    println { "round trip took average of: " + (t/N.toDouble) + " milliseconds" }
    true
  }

  // NB: this property should always appear last, so it runs after all properties have run
  property("cleanup") = lazily {
    server()
    akkaTrans.pool.close()
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
