# Remotely

[![Build Status](https://jenkins.svc.oncue.com:8443/view/Bake/job/WebServices-remotely/badge/icon)](https://jenkins.svc.oncue.com:8443/view/Bake/job/WebServices-remotely/)

A safe, convenient RPC system fo reasonable applications.

```Scala
> test:console
[info] Compiling 2 Scala sources to /projects/remotely/target/scala-2.10/classes...
[info] Starting scala interpreter...
[info]
Welcome to Scala version 2.10.3 (Java HotSpot(TM) 64-Bit Server VM, Java 1.7.0_45).
Type in expressions to have them evaluated.
Type :help for more information.

scala> import remotely._
import remotely._

scala> import codecs._
import codecs._
```

Let's build up a `remotely.Protocol` with just a single `Int` codec, and the factorial function specified to be of type `Int => Int`:

```Scala
scala> val facProtocol = Protocol.empty.codec[Int].specify[Int => Int]("fac")
facProtocol: remotely.Protocol =
Protocol(
  Codecs(
    Decoders.empty
      .decoder[Int],
    Encoders.empty
      .encoder[Int]
  ),
  Signatures.empty
    .specify[Int => Int]("fac")
)
```

(Kind of cute that the `toString` for `Protocol` actually generates valid Scala.)

We generate the client module from this `Protocol`, then paste it back into the REPL to evaluate it. If we were doing this for real, we'd obviously save the result to some file, and possibly add some further documentation to it.

```Scala
scala> facProtocol.generateClient("FactorialsClient")
res1: String =
"
import remotely.Remote

object FactorialsClient {
  // This module contains code generated from a `remotely.Protocol`. Do not alter.
  val fac = Remote.ref[Int => Int]("fac")
}
"

scala> :pa
// Entering paste mode (ctrl-D to finish)

import remotely.Remote

object FactorialsClient {
  // This module contains code generated from a `remotely.Protocol`. Do not alter.
  val fac = Remote.ref[Int => Int]("fac")
}

// Exiting paste mode, now interpreting.

import remotely.Remote
defined module FactorialsClient
```

By default, `Codec`-able primitives aren't promoted to `Remote` values:

```Scala
scala> FactorialsClient.fac(8)
<console>:16: error: type mismatch;
 found   : Int(8)
 required: remotely.Remote[Int]
              FactorialsClient.fac(8)
                                   ^
```

We could do `FactorialsClient.fac(Remote.local(8))` if we wanted to be very explicit about where we're lifting values into `Remote`. Or we can just import the implicits for this:

```Scala
scala> import Remote.implicits._
import Remote.implicits._

scala> FactorialsClient.fac(8)
res3: remotely.Remote[Int] = fac(8)
```

That takes care of the client. Now let's generate the server:

```Scala
scala> res0.generateServer("FactorialsServer")
res4: String =
"
import remotely.{Codecs,Decoders,Encoders,Environment,Values}

trait FactorialsServer {
  // This interface is generated from a `Protocol`. Do not modify.
  def environment: Environment = Environment(
    Codecs(
      Decoders.empty
        .decoder[Int],
      Encoders.empty
        .encoder[Int]
    ),
    populateDeclarations(Values.empty)
  )

  def fac: Int => Int

  private def populateDeclarations(env: Values): Values = env
    .declare[Int => Int]("fac") { fac }
}
  "
```

Notice that `fac` (the name we specified in the `Protocol`) is abstract. All we have to do is implement this `trait`, we'll be forced to fill in the definition for `fac`, and the rest of the plumbing is done for us, in the `FactorialServer` generated `trait`.

Let's evaluate that code we just generated. Again, it would be more realistic to place this generated code in a file.

```Scala
scala> :pa
// Entering paste mode (ctrl-D to finish)

import remotely.{Codecs,Decoders,Encoders,Environment,Values}

trait FactorialsServer {
  // This interface is generated from a `Protocol`. Do not modify.
  def environment: Environment = Environment(
    Codecs(
      Decoders.empty
        .decoder[Int],
      Encoders.empty
        .encoder[Int]
    ),
    populateDeclarations(Values.empty)
  )

  def fac: Int => Int

  private def populateDeclarations(env: Values): Values = env
    .declare[Int => Int]("fac") { fac }
}

// Exiting paste mode, now interpreting.

import remotely.{Codecs, Decoders, Encoders, Environment, Values}
defined trait FactorialsServer
```

Okay, we now have the server interface defined, so let's try using it. The Scala compiler alerts us if we've forgotten to define the abstract `fac` method specified in the `Protocol`:

```Scala
scala> val theServer = new FactorialsServer
<console>:19: error: trait FactorialsServer is abstract; cannot be instantiated
       val theServer = new FactorialsServer
                       ^

scala> val theServer = new FactorialsServer {}
<console>:19: error: object creation impossible, since method fac in trait FactorialsServer of type => Int => Int is not defined
       val theServer = new FactorialsServer {}
                           ^

scala> val theServer = new FactorialsServer { def fac = (i: Int) => (1 to i).product }
theServer: FactorialsServer = $anon$1@6af562c9
```

To actually run the server, we can do:

```Scala

scala> val addr = new java.net.InetSocketAddress("localhost", 8080)
addr: java.net.InetSocketAddress = localhost/127.0.0.1:8080

scala> theServer.environment.serve(addr)
res6: () => Unit = <function0>

scala> [INFO] [03/19/2014 11:14:31.271] [rpc-server-akka.actor.default-dispatcher-2] [akka://rpc-server/user/$a] server bound to: /127.0.0.1:8080
```

`res6` is a thunk we can use to terminate the server. Now that the server is running, let's try evaluating `fac(8)` from the client. Recall:

```Scala
scala> FactorialsClient.fac(8)
res7: remotely.Remote[Int] = fac(8)
```

Applying the remote `fac` function to `8` yields a result of type `Remote[Int]`, as we expect. This is just a syntax tree at this point, nothing has been submitted to the server, and no networking has begun yet. To run the `Remote[Int]`, we just need an `Endpoint`:

```Scala
scala> import akka.actor._
import akka.actor._

scala> implicit val clientPool = ActorSystem("rpc-client")
clientPool: akka.actor.ActorSystem = akka://rpc-client

scala> res7.run(Endpoint.single(addr))
res10: scalaz.concurrent.Task[Int] = scalaz.concurrent.Task@33a19e83
```

Again, calling `run` on the `Remote[Int]` just converts to a `Task[Int]`. No networking occurs until we actually invoke `run` on that `Task`. When we do call `run` on the `Task`, this will use the [scodec library](https://github.com/scodec/scodec) to serialize the `fac(8)` expression, connect to the server using an `akka.io` client, and asynchronously await the server result. On the server side, the `akka.io`-based server will await requests, decode them to a `Remote` value, evaluate that remote value using the definition for `fac` provided by `theServer`, and send back the response.

```Scala
scala> res10.run
res11: Int = 40320
```

And since `Task` is just a pure value, we can call `run` again to repeat the entire process.

```Scala
scala> res10.run
res12: Int = 40320

scala> res10.run
res13: Int = 40320
```

Finally, there are several possible failure modes, and where possible, the server attempts to 'fail fast':

* Server may not have a needed decoder in its environment. In this case, it will report back to the client a meaningful error about the type of decoder it is missing.
* Server may have all the needed decoders, but decoding may fail for some reason (perhaps the input was encoded improperly, or a gamma ray flipped some bits en route). In this case, the server reports the decoding error message produced by scodec.
* Server may be missing the encoder for the response type. In this case, it will report back to the client a meaningful error about the type of encoder it is missing, before it begins evaluation.
* Server may be missing a referenced declaration, or the client may be expecting it to be of a different type. Both cases are treated the same way. For instance, if `fac` had the type `Double => Double` on the server, this would be an error that the server could not find a declaration `fac: Int => Int`, which the client was expecting. See below for an example.
* Server may fail during evaluation. In this case, the stack trace and message are sent back to the client.
* Server may fail during encoding the response. In this case, the server reports the encoding error message produced by scodec.

Here are couple examples of these error conditions:

```
java.lang.Exception: remotely.Server$Error: [decoding] server does not have deserializers for:
Float
List[Float]

java.lang.Exception: remotely.Server$Error: [validation] server does not have referenced values:
product: List[Int] => Int
```

The philosophy here is that the collection of names and signatures constitute the 'version' of the service, and remotely just verifies that the types line up between the client and the server, and reports a meaningful error if not. The RPC request will proceed as long as the individual client request is a subprotocol of what the server knows about. Thus, it is perfectly fine for the server to add new functions over time, without necessarily having to alter existing clients.

Of course, it might make sense to add a separate layer of versioning beyond this 'structural' versioning done by remotely, but that can be handled with a separate layer.