package remotely

import akka.actor.{Actor,ActorLogging,ActorSystem,OneForOneStrategy,Props,SupervisorStrategy}
import akka.io.{BackpressureBuffer,IO,Tcp,SslTlsSupport,TcpPipelineHandler}
import akka.util.ByteString
import java.net.{InetSocketAddress,Socket,URL}
import javax.net.ssl.SSLEngine
import scalaz.concurrent.Strategy
import scalaz.std.anyVal._
import scalaz.concurrent.Task
import scalaz.syntax.functor._
import scalaz.stream.{async,Channel,Exchange,io,Process,nio,Process1, process1, Sink}
import scodec.bits.{BitVector,ByteVector}
import Endpoint.Connection
import scala.concurrent.duration._
import scalaz._
import Process.{Await, Emit, Halt, emit, await, halt, eval, await1, iterate}
import scalaz.stream.merge._

/**
 * A 'logical' endpoint for some service, represented
 * by a possibly rotating stream of `Connection`s.
 */
case class Endpoint(connections: Process[Task,Connection]) {
  def get: Task[Connection] = connections.once.runLast.flatMap {
    case None => Task.fail(new Exception("No available connections"))
    case Some(a) => Task.now(a)
  }

  /**
   * Adds a circuit-breaker to this endpoint that "opens" (fails fast) after
   * `maxErrors` consecutive failures, and attempts a connection again
   * when `timeout` has passed.
   */
  def circuitBroken(timeout: Duration, maxErrors: Int): Task[Endpoint] =
    CircuitBreaker(timeout, maxErrors).map { cb =>
      Endpoint(connections.map(c => bs => c(bs).translate(cb.transform)))
    }

  import Endpoint._

}

object Endpoint {

  /**
   * If a connection in an endpoint fails, then attempt the same call to the next
   * endpoint, but only if `timeout` has not passed AND we didn't fail in a
   * "committed state", i.e. we haven't received any bytes.
   */
  def failoverChain(timeout: Duration, es: Process[Task, Endpoint]): Endpoint =
    Endpoint(transpose(es.map(_.connections)).flatMap { cs =>
               cs.reduce((c1, c2) => bs => c1(bs) match {
        case w@Await(a, k) =>
          await(time(a.attempt))((p: (Duration, Throwable \/ Any)) => p match {
            case (d, -\/(e)) =>
              if (timeout - d > 0.milliseconds) c2(bs)
              else eval(Task.fail(new Exception(s"Failover chain timed out after $timeout")))
            case (d, \/-(x)) => k(\/-(x)).run
          })
        case x => x
      })
    })

  /**
   * An endpoint backed by a (static) pool of other endpoints.
   * Each endpoint has its own circuit-breaker, and fails over to all the others
   * on failure.
   */
  def uber(maxWait: Duration,
           circuitOpenTime: Duration,
           maxErrors: Int,
           es: Process[Task, Endpoint]): Endpoint =
    Endpoint(mergeN(permutations(es).map(ps =>
      failoverChain(maxWait, ps.evalMap(p => p.circuitBroken(circuitOpenTime, maxErrors))).connections)))

  /**
   * Produce a stream of all the permutations of the given stream.
   */
  def permutations[A](p: Process[Task, A]): Process[Task, Process[Task, A]] = {
    val xs = iterate(0)(_ + 1) zip p
    for {
      b <- eval(isEmpty(xs))
      r <- if (b) emit(xs) else for {
        x <- xs
        ps <- permutations(xs |> delete { case (i, v) => i == x._1 })
      } yield emit(x) ++ ps
    } yield r.map(_._2)
  }

  /** Skips the first element that matches the predicate. */
  def delete[I](f: I => Boolean): Process1[I,I] = {
    def go(s: Boolean): Process1[I,I] =
      await1[I] flatMap (i => if (s && f(i)) go(false) else emit(i) ++ go(s))
    go(true)
  }

  /**
   * Transpose a process of processes to emit all their first elements, then all their second
   * elements, and so on.
   */
  def transpose[F[_]:Monad: Catchable, A](as: Process[F, Process[F, A]]): Process[F, Process[F, A]] =
    emit(as.flatMap(_.take(1))) ++ eval(isEmpty(as.flatMap(_.drop(1)))).flatMap(b =>
      if(b) halt else transpose(as.map(_.drop(1))))

  /**
   * Returns true if the given process never emits.
   */
  def isEmpty[F[_]: Monad: Catchable, O](p: Process[F, O]): F[Boolean] = ((p as false) |> Process.await1).runLast.map(_.getOrElse(true))


  def time[A](task: Task[A]): Task[(Duration, A)] = for {
    t1 <- Task(System.currentTimeMillis)
    a  <- task
    t2 <- Task(System.currentTimeMillis)
    t3 <- Task(t2 - t1)
  } yield (t3.milliseconds, a)

  type Connection = Process[Task,ByteVector] => Process[Task, ByteVector]

  // combinators for building up Endpoints

  def empty: Endpoint = Endpoint(Process.halt)

  def single(host: InetSocketAddress)(implicit S: ActorSystem): Endpoint =
    Endpoint(Process.constant(akkaRequest(S)(host, None)))

  def singleSSL(createEngine: () => SSLEngine)(
                host: InetSocketAddress)(implicit S: ActorSystem): Endpoint =
    Endpoint(Process.constant(akkaRequest(S)(host, Some(createEngine))))

  private def akkaRequest(system: ActorSystem)(
      host: InetSocketAddress,
      createEngine: Option[() => SSLEngine] = None): Connection = out => {
    val src = async.unboundedQueue[ByteVector](Strategy.Sequential)
    val queue = src.dequeue
    @volatile var normal = false // did the logic of this request complete gracefully?
    val actor = system.actorOf(Props(new Actor with ActorLogging {
      import context.system

      override def preStart() =
        IO(Tcp)(context.system) ! Tcp.Connect(host)

      // PC: This seemingly does nothing - I'd expect child actors to report errors here,
      // but they don't for some reason
      override val supervisorStrategy = OneForOneStrategy() {
        case err: Throwable =>
          log.error("failure: " + err)
          src.fail(err).run
          SupervisorStrategy.Stop
      }

      def receive = {
        case Tcp.CommandFailed(_: Tcp.Connect) ⇒
          log.error("connection failed to " + host)
          src.fail(new Exception("connection failed to host " + host)).run
          context stop self
        case c @ Tcp.Connected(remote, local) =>
          val connection = sender
          val core = context.system.actorOf(Props(new Actor with ActorLogging { def receive = {
           case Tcp.Received(data) => src.enqueueOne(ByteVector(data.toArray)).run
            case Tcp.Aborted => src.fail(new Exception("connection aborted")).run; normal = true
            case Tcp.ErrorClosed(msg) => src.fail(new Exception("I/O error: " + msg)).run; normal = true
            case _ : Tcp.ConnectionClosed => src.close.run; normal = true; context stop self
          }}))

          val (writeBytes, pipeline) = createEngine.map { engine =>
            val sslEngine = engine()
            log.debug("client enabled cipher suites: " + sslEngine.getEnabledCipherSuites.toList)
            val init = TcpPipelineHandler.withLogger(log, new SslTlsSupport(sslEngine))
            val pipeline = context.actorOf(TcpPipelineHandler.props(init, connection, core))
            Akka.onComplete(context.system, pipeline) {
              // Did we complete normally? If not, raise an exception
              if (!normal) src.fail(new Exception(
                "SSL pipeline terminated, most likely because of an error in negotiating SSL session")
              ).run
            }
            val writeBytes = (bs: ByteVector) =>
              pipeline ! init.Command(Tcp.Write(ByteString(bs.toArray)))
            (writeBytes, pipeline)
          } getOrElse {
            ((bs: ByteVector) => connection ! Tcp.Write(ByteString(bs.toArray)), core)
          }

          // Underlying connection needs to `keepOpenOnPeerClosed` if using SSL
          // NB: the client does not close the connection; the server closes the
          // connection when it is finished writing (or in the event of an error)
          connection ! Tcp.Register(pipeline, keepOpenOnPeerClosed = createEngine.isDefined)

          // write all the bytes to the connection, this must happen AFTER the Tcp.Register above
          out.evalMap { bytes => println(s"bytesssss: + $bytes") ; Task.delay { writeBytes(bytes) } }
            .run.runAsync { e => e.fold(
                             e => { normal = true; src.fail(e).run; context stop self },
                             _ => { context stop self }
                           )}
      }
    }))
    queue
  }

  def streamExchange(exch: Exchange[ByteVector,ByteVector]): Process[Task,ByteVector] => Process[Task,ByteVector] =
    bytes => bytes.to(exch.write).drain ++ exch.read

  def connect(host: InetSocketAddress): Process[Task, Exchange[ByteVector, ByteVector]] =
    Process.eval(Task.delay(new Socket(host.getAddress, host.getPort))).map { socket =>
      val in = socket.getInputStream

      val read  = forked(Process.constant(4096))
                  .through(io.chunkR(in))
                  .onComplete { Process.eval(Task.delay(socket.shutdownInput)).attempt().drain }

      val write = forked(io.chunkW(socket.getOutputStream))
                  .map { a => println("socket closed: " + socket.isClosed); a }
                  .onComplete { Process.eval(Task.delay(socket.shutdownOutput)).attempt().drain }

      Exchange[ByteVector,ByteVector](read, write)
    }

  def forked[A](p: Process[Task,A]): Process[Task,A] =
    p.evalMap(a => Task(a))

  def roundRobin(p: Endpoint*): Endpoint = {
    require(p.nonEmpty, "round robin must have at least one endpoint to choose from")
    val pts = p.toIndexedSeq
    reduceBalanced(pts)(_ => 1)((a,b) => Endpoint(a.connections.interleave(b.connections)))
  }

  private def reduceBalanced[A](v: TraversableOnce[A])(size: A => Int)(
                      f: (A,A) => A): A = {
    @annotation.tailrec
    def fixup(stack: List[(A,Int)]): List[(A,Int)] = stack match {
      // h actually appeared first in `v`, followed by `h2`, preserve this order
      case (h2,n) :: (h,m) :: t if n > m/2 =>
        fixup { (f(h, h2), m+n) :: t }
      case _ => stack
    }
    v.foldLeft(List[(A,Int)]())((stack,a) => fixup((a -> size(a)) :: stack))
     .reverse.map(_._1)
     .reduceLeft(f)
  }

  def logical(name: String)(resolve: String => Endpoint): Endpoint =
    resolve(name)

}
