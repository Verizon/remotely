# Remotely

Remotely is a purely functional remoting library for reasonable people. It's fast, lightweight and models network operations as explicit monadic computations.

<a name="rationale"></a>

## Rationale

Before talking about *how* to use Remotely, its worth discussing *why* this project exists.

For large distributed service platforms there is typically a lot of inter-service communication. In this scenario, certain things become really important: 

* **Productivity**. While much progress can be made with the HTTP protocol, together with serialisation technologies commonly used for services (e.g. JSON, XML etc), in larger teams we observe considerable development effort being spent on transforming `AST => A` and `A => AST`, where the AST is the wire format representation (e.g. `JValue` from lift-json or similar). As these ASTs typically represent some kind of semi-structured data, programmers have to then waste time traversing the AST (with very limited reuse) to get the typed value they care about. In *Remotely* we try to address this by providing a generic way to serialise any `A` over the wire. This removes the need for traversing various types of AST to extract the needed fields as the serialisation / deserialization code is highly composable (thanks to [scodec](https://github.com/scodec/scodec))

* **Safety**. Something that is missing from HTTP+JSON services is the ability to know if a given remote service is compatible with our way of calling it. Especially when moving between revisions of an API. Typically this kind of meta information ends up being encoded in a version number, or some other out-of-band knowledge. This often results in runtime failures and incompatibility between services unless exceptional care is taken by the service owner not to break their API in any way. Using *Remotely* we build the protocols just like any other compile-time artifact and we can rely on Scala's type system to check compatibility for us.

* **Reuse**. In most typed protocol definitions there is a low degree of reuse because protocol specifications are not compositional. An example of this is Thrift's protocol definitions: the definition contains all structures and values used by the client and server, and the entire world is generated at build time, even if that exact same structure (e.g tuples) is used by an adjacent service. *Remotely* instead relies on a protocol specification that is just a Scala data type with an associated combinator library.

<a name="getting-started"></a>

## Getting Started

What follows is a typical scenario for setting up a project that uses Remotely.

### Dependency Information

Remotely has the following dependencies:

* `Scalaz 7.0.6`
* `Scodec 1.1.3`
* `Akka I/O 2.2.4`
* `Shapeless 2.0.0` (transitively via scodec)

This is an important factor to keep in mind, as if you have clashing versions of these libraries on your classpath you will encounter strange runtime failures due to binary incompatibility.

### Project & Build Setup

Typically you want to separate your wire protocol from your core domain logic (so that the two can evolve independently over time). With Remotely this matters because the protocol definition is designed to be depended on by callers, so they can get compile-time errors if a particular service breaks compatibility. With this in mind, the following layout is recommended:

```
.
├── CHANGELOG
├── README.md
├── core
│   ├── build.sbt
│   └── src
│       ├── main
│       └── test
├── project
│   ├── build.properties
│   └── plugins.sbt
├── project.sbt
├── rpc
│   ├── build.sbt
│   └── src
│       └── main
├── rpc-protocol
│   ├── build.sbt
│   └── src
│       └── main
└── version.sbt
```

The structure breaks down like this:

* `core` contains the domain logic for your application; ideally you want to make `core` self-contained and fully testable without any kind of wire protocol.

* `protocol` is a simple compilation unit that only contains the definition of the protocol (not the implementation) - more on this below

* `rpc` contains the implementation of the protocol. This is where you translate between your domain types and their wire representations. This module should depend on the `rpc` and `core` modules.

* `project` is the usual SBT build information.

Once you have the layout configured, using Remotely is just like using any other library in SBT; simply add the dependency to your `protocol` module:

```
libraryDependencies += "oncue.svc.remotely" %% "core" % "1.1.+"
```

### Protocol Definition

The first thing that *Remotely* needs is to define a `Protocol`. A protocol is essentially a specificaion of the interface this server should expose to callers. Consider this example from `rpc-protocol/src/main/scala/protocol.scala`:

```
package oncue.svc.example

import remotely._, codecs._

object protocol {
  val definition = Protocol.empty
    .codec[Int]
    .specify[Int => Int]("factorial")
}

```

This protocol exposes a single function, called `factorial` that expects an `Int` from the caller and responds by sending an `Int` back.

Protocols are the core of the remotely project. They represent the contract between the client and the server, and they contain all the plumbing needed to make that service work properly. The protocol object supports the following operations:

* `empty`: create a new, empty protocol as a starting point for building out a service contract.

* `codec`: require a `Codec` for the specified type. If the relevant `Codec` cannot be found, you will either have to import one or define your own. By default, *Remotely* comes with implementations for `String`, `Int`, and the majority of default types you might need. You can still define `Codec` implementations for any of your own structures as needed.

* `specify`: define a function that the contract will have to implement. In our example, we want to expose a "factorial" function that will take an `Int` and return an `Int`.

### Server & Client Definition

*Remotely* makes use of compile-time macros to build out interfaces for server implementations and client objects. Given that your service `Protocol` is defined in the `rpc-protocol` module, we can access that protocol as a regular Scala value from our `rpc` module. This enables us to generate servers and clients at compile time. Here's an example:

```
package oncue.svc.example

import remotely._

// NB: The GenServer macro needs to receive the FQN of all types, or import them
// explicitly. The target of the macro needs to be an abstract class.
@GenServer(oncue.svc.example.protocol.definition)
abstract class FactorialServer

class FactorialServer0 extends FactorialServer {
  val factorial: Int => Response[Int] = n =>
    Response.now { (1 to n).product }
}

```

The `GenServer` macro does require all the FQCN of all the inputs, but provided you organize your project as above, the only input you need is a protocol you defined in your `rpc-protocol` module. Importantly, that value is available at _compile time_ in your `rpc` module. You cannot pass a value to the `GenServer` macro if that value is defined in the same compilation unit.

Generating a client is also very simple. While `GenServer` generates an interface that your server must implement, the client generated by `GenClient` is fully complete, and includes the implementation.

```
package oncue.svc.example

import remotely._

// The `GenClient` macro needs to receive the FQN of all types, or import them
// explicitly. The target needs to be an object declaration.
@GenClient(oncue.svc.example.protocol.definition.signatures)
object FactorialClient

```

That's all that is needed to define both a client and a server. 

### Putting it Together

With everything defined, you can now make choices about how best to wire everything together. For now we'll focus on a simple implementation, but the Remotely manual has more information on endpoints, circuit breakers, monitoring, TLS configuration etc.

Here's the `main` for the server:

```
package oncue.svc.example

import java.net.InetSocketAddress
import remotely._, codecs._

object Main {

  def main(args: Array[String]): Unit = {
  
    val address  = new InetSocketAddress("localhost", 8080)
    
    val service = new FactorialServer0
    
    val env = service.environment

    val shutdown = env.serve(address)(Monitoring.empty)

  }
}
```

This is super straightforward, but let's look at the values one by one. 

* `address`: The network location the server process will bind to on the host machine.

* `service`: An instance of the server implementation (which in turn implements the macro-generated interface based on the protocol)

* `env`: For any given service implementation, an `Environment` can be derived from it. Unlike the protocol implementaiton itself, `Environemnt` is a concrete thing that can be bound and executed at runtime, whereas `Protocol` is primarily a compile-time artifact.

* `shutdown`: Upon calling `env.serve(...)` the process will bind to the specified address and yield a `() => Unit` function that can be used to shutdown the process if needed.

The client is similar:

```
package oncue.svc.example

import scalaz.concurrent.Task
import java.net.InetSocketAddress
import remotely._, codecs._

object Main {
  import Remote.implicits._

  def main(args: Array[String]): Unit = {

    val address  = new InetSocketAddress("localhost", 8080)

    val system = akka.actor.ActorSystem("rpc-client")

    val endpoint = Endpoint.single(address)(system)

    val f: Remote[Int] = FactorialClient.factorial(7)
	
    val task: Task[Int] = f.runWithoutContext(endpoint)
    
    // then at the edge of the world, run it and print to the console
    task.map(println(_)).runAsync(_ => ())
  }
}

```

* `address`: The address of the server. 

* `system`: This is the underlying Akka system used to power the Akka IO networking. This is mandatory and provides necessary settings for thread pooling, backoff, etc.

* `endpoint`: An `Endpoint` is an abstraction over callable service locations. The `Endpoint` type has a rich set of combinators, but for this simple example we simply construct a fixed, static, single location. More information can be found in the [manual](http://).

* `f`: Application of the remote function reference. Here we pass in the arguments needed by the remote function, and at compile time you will be forced to ensure that you have a `Codec` for all the arguments supplied, and said arguments must be in scope within that compilation unit (i.e. if the remote service uses custom structures you'll need to ensure you import those accordingly). At this point *no request has actually been made over the wire*.

* `task`: In order to do something useful with the `Remote` instance its nessicary to make a choice about what "context" this remote operation will be executed in. Again, this is covered in the [manual](http://), but for now we shall elect to run without a context, by way of the `runWithoutContext` function. There still has been no network I/O; we have simply applied the function operation and transformed it into a scalaz `Task`.

Finally, the function does network I/O to talk to the server when the `Task` is executed (using the `runAsync` method here). You can learn more about the `Task` monad [on this blog post](http://timperrett.com/2014/07/20/scalaz-task-the-missing-documentation/).

