package remotely

import collection.immutable.SortedSet
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import codecs._, Response.Context
import akka.actor.ActorSystem
import codecs._
import transport.akka._

class ProtocolSpec extends FlatSpec
  with Matchers
  with BeforeAndAfterAll
{
  lazy val system = ActorSystem("test-client")
  val addr = new java.net.InetSocketAddress("localhost", 9000)
  val server = new TestServer
  val shutdown: () => Unit = server.environment.serve(addr)(Monitoring.empty)


  val endpoint = Endpoint.single(AkkaTransport.single(system,addr))

  it should "foo" in {
    import remotely.Remote.implicits._
    val fact: Int = evaluate(endpoint, Monitoring.consoleLogger())(Client.factorial(10)).apply(Context.empty).run
    val lst: List[Int] = Client.foo(9).runWithContext(endpoint, Context.empty).run
  }

  override def afterAll(){
    Thread.sleep(500)
    shutdown()
    system.shutdown()
  }
}


trait TestServerBase {

  import Codecs._

  def environment: Environment = Environment(
    Codecs.empty
      .codec[Int]
      .codec[List[Int]],
    populateDeclarations(Values.empty)
  )

  def factorial: Int => Response[Int]
  def foo: Int => Response[scala.List[Int]]

  private def populateDeclarations(env: Values): Values = env
    .declare("factorial", factorial)
    .declare("foo", foo)
}

class TestServer extends TestServerBase {
  implicit val intcodec = int32
  def factorial: Int => Response[Int] = i => Response.now(i * i)
  def foo: Int => Response[List[Int]] = i =>  {
    Response.now(collection.immutable.List.fill(10000)(i))
  }
}

object Client {
  val factorial = Remote.ref[Int => Int]("factorial")
  val foo = Remote.ref[Int => scala.List[Int]]("foo")
}
