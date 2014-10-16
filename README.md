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

We can generate the client module source code from this `Protocol`, then paste it back into the REPL to evaluate it.

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

We can also use the `GenClient` macro to generate the client object directly from the protocol's signatures. This is the recommended method of generating clients in your projects. Note that this _does not work in the REPL:_

```Scala
@GenClient(remotely.Protocol.empty
  .codec[Int]
  .specify[Int => Int]("fac").signatures) object FactorialsClient
```

Important: In a static macro annotation like `@GenClient(p.signatures)`, the protocol `p` must be a _statically_ available object (meaning available at compile time). So we have to either fully specify it inline as above, or we must build it in a project that is compiled separately.

I.e. this does not work:

```Scala
import remotely._
val p = Protocol.empty.codec[Int].specify[Int => Int]("fac")
@GenClient(p.signatures) object FactorialsClient
```

But this does:

```Scala
@GenClient({
  import remotely._
  val p = Protocol.empty.codec[Int].specify[Int => Int]("fac")
  p
}) object FactorialsClient
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

That takes care of the client. Now let's generate the server. We can use the `GenServer` macro (recommended for your projects, but macros can't be used in the REPL):

```Scala
@GenServer(remotely.Protocol.empty.codec[Int].specify[Int => Int]("fac"))
  abstract class FactorialsServer
```

We can also generate the source code for the server to try things out in the REPL:

```Scala
scala> res0.generateServer("FactorialsServer")
res4: String =
"
import remotely.{Codecs,Decoders,Encoders,Environment,Values}

abstract class FactorialsServer {
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

  def fac: Int => Response[Int]
  
  private def populateDeclarations(env: Values): Values = env
    .declare[Int => Int]("fac") { fac }
}
  "
```

Notice that `fac` (the name we specified in the `Protocol`) is abstract. All we have to do is implement `FactorialsServer`, we'll be forced to fill in the definition for `fac`, and the rest of the plumbing is done for us in the `FactorialsServer` generated `class`.

Let's evaluate that code we just generated. Again, in a real project you should use the `GenServer` macro instead.

```Scala
scala> :pa
// Entering paste mode (ctrl-D to finish)

import remotely.{Codecs,Decoders,Encoders,Environment,Values}

abstract class FactorialsServer {
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

  def fac: Int => Response[Int]
  
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

scala> val theServer = new FactorialsServer {
     |   def fac = (i: Int) => Response.now { (1 to i).product) }
     | }
theServer: FactorialsServer = $anon$1@6af562c9
```

To actually run the server, we can do:

```Scala

scala> val addr = new java.net.InetSocketAddress("localhost", 8080)
addr: java.net.InetSocketAddress = localhost/127.0.0.1:8080

scala> theServer.environment.serve(addr)()
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

scala> res7.runWithoutContext(Endpoint.single(addr))
res10: remotely.Response[Int] = remotely.Response$$anon$3@7462823f
```

Again, calling `runWithoutContext` on the `Remote[Int]` just converts to a `Task[Int]`. No networking occurs until we actually invoke `run` on that `Task`. When we do call `run` on the `Task`, this will use the [scodec library](https://github.com/scodec/scodec) to serialize the `fac(8)` expression, connect to the server using an `akka.io` client, and asynchronously await the server result. On the server side, the `akka.io`-based server will await requests, decode them to a `Remote` value, evaluate that remote value using the definition for `fac` provided by `theServer`, and send back the response.

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

Here are some examples of these error conditions:

```
java.lang.Exception: remotely.Server$Error: [decoding] server does not have deserializers for:
Float
List[Float]

java.lang.Exception: remotely.Server$Error: [validation] server does not have referenced values:
product: List[Int] => Int
```

The philosophy here is that the collection of names and signatures constitute the 'version' of the service, and remotely just verifies that the types line up between the client and the server, and reports a meaningful error if not. The RPC request will proceed as long as the individual client request is a subprotocol of what the server knows about. Thus, it is perfectly fine for the server to add new functions over time, without necessarily having to alter existing clients.

Of course, it might make sense to add a separate layer of versioning beyond this 'structural' versioning done by remotely, but that can be handled with a separate layer.
