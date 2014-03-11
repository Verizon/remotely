package srpc

import java.net.InetSocketAddress
import org.scalacheck._
import Prop._

object RemoteSpec extends Properties("Remote") {

  import Codecs._
  import Remote.implicits._

  val env = Environment.empty
    .codec[List[Int]]
    .codec[Int]
    .declare("sum") { (d: List[Int]) => d.sum }

  val sum = Remote.ref[List[Int] => Int]("sum")

  val addr = new InetSocketAddress("localhost", 8080)

  property("sum") = secure {
    val server = Server.start(env)(addr)
    // to actually run a remote expression, we need an endpoint
    implicit val clientPool = akka.actor.ActorSystem("rpc-client")
    val loc: Endpoint = Endpoint.single(addr) // takes ActorSystem implicitly

    val prop = forAll { (l: List[Int]) => l.sum == sum(l).run(loc).run }

    onComplete (prop) {
      //server()
      //Thread.sleep(1000)
      //clientPool.shutdown()
    }
  }

  def onComplete(p: => Prop)(action: => Unit): Prop = {
    var done = false
    p || secure { done = true; action; false } && secure { if (done) true else { action; true }}
  }

}
