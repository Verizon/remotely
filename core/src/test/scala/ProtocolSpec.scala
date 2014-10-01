package remotely

import collection.immutable.SortedSet
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import codecs._, Response.Context
import akka.actor.ActorSystem
import codecs._

class ProtocolSpec extends FlatSpec
  with Matchers
  with BeforeAndAfterAll
{
  lazy val system = ActorSystem("test-client")
  val addr = new java.net.InetSocketAddress("localhost", 9000)
  val server = new TestServer
  val shutdown: () => Unit = server.environment.serve(addr)(Monitoring.consoleLogger())

  val endpoint = Endpoint.single(addr)(system)

  override def beforeAll(){
    println(">>>> beforee")
  }

  it should "foo" in {
    import remotely.Remote.implicits._

    println("factorial: " + Client.factorial(10).runWithContext(endpoint, Context.empty).run)
    println("foo: " + Client.foo(9).runWithContext(endpoint, Context.empty).run)
  }

  override def afterAll(){
    println(">>>> after")
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
  def factorial: Int => Response[Int] = i => Response.now(i * i)
  def foo: Int => Response[List[Int]] = i => 
    Response.now(collection.immutable.List(i,i,i,i))
}

object Client {
  val factorial = Remote.ref[Int => Int]("factorial")
  val foo = Remote.ref[Int => scala.List[Int]]("foo")
}
