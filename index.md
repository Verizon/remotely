---
layout: default
title:  "Home"
section: "home"
---

# Introduction

Remotely is an elegant, reasonable, purely functional remoting system. Remotely is fast, lightweight and models network operations as explicit monadic computations. Remotely is ideally suited for:

* Client/server programming
* Service-to-service communication
* Building network-facing APIs

<div class="msg warn">
  <p><strong>NB:</strong> Remotely is currently an experimental project under active development. Feedback and contributions are welcomed as we look to improve the project.</p>
</div>

<a name="rationale"></a>

## Rationale

Before talking about how to *use* Remotely, it's worth discussing why we made this project in the first place. For large distributed service platforms there is typically a large degree of inter-service communication. In this scenario, several factors become really important:

* **Productivity**. We want to spend less of our time writing marshalling/unmarshalling boilerplate. Although much progress has been made with things like JSON or XML over HTTP, in larger teams development time is typically spent on transforming to and from some internal representation of the wire content syntax tree (e.g. `JValue` from lift-json or similar). As this AST is typically some kind of semi-structured data, users have to waste time traversing all these different JSON structures (with either none or extremely minimal reuse) to extract the typed value they care about. Alternatively, some marshalling/unmarshalling libraries resort to runtime reflection or similar magic, which developers find confusing and frustrating. In *Remotely* we have tried to address this by providing a generic way to serialise any given type over the wire. This immediately removes the need for traversing various types of AST to extract fields, since the serialisation and deserialization code is highly composable and modular (thanks to [scodec](https://github.com/scodec/scodec)).

* **Safety**. In *Remotely*, incompatibilities between client and server result in a compile-time rather than run-time failure. Something that is missing from HTTP+JSON services is the ability to know that clients remain compatible when moving between revisions of a service API. Typically this kind of meta-information ends up being encoded in a version number or some other out-of-band knowledge. This often results in runtime failures and incompatibility between services unless exceptional care is taken by the service owner not to break their API in any way between revisions. Using *Remotely* we build the protocols just like any other compile-time artifact. Those artefacts are then published as JARs and depended upon as build-time contracts by clients. It is then easy to build all dependent services as downstream jobs during the build phase, which gives engineers early warnings about compatibility issues with their service APIs.

* **Reuse**. In most typed-protocol definitions there is a low degree of reuse because the serialisation code does not compose, and generally has no higher-order facilities. An example of this would be Thrift's protocol definitions: the definition contains all structures and values used by the client and server, and the entire world of needed files is generated at build time, even if that exact same structure (e.g tuples) is used by an adjacent service. Within *Remotely* we wanted to avoid this nasty code-generation step and instead rely on highly composable structures with their associated combinators to get the granularity and level of reuse we wanted.

<a name="getting-started"></a>

## Getting Started

Using remotely is straightforward, and getting started on a new project could not be simpler!

### Dependency Information

Remotely has the following dependencies:

* `Scalaz 7.1.0`
* `Scalaz Stream 0.6a`
* `Scodec 1.5.0`
* `Apache Commons Pool 2.2`
* `Netty 3.6.6 Final`
* `Macro Paradise 2.0.1`
* `Shapeless 2.0.0` (transitively via scodec)

This is an important factor to keep in mind, as if you have clashing versions of these libraries on your classpath you will encounter strange runtime failures due to binary incompatibility.

### Project & Build Setup

Typically you want to separate your wire protocol from your core domain logic (this is true even in HTTP applications of course). With remotely this matters because the protocol definition is designed to be depended on by callers, so they can achieve compile-time failures if a particular service breaks its API in an incompatible fashion. With this in mind, the following layout is advised to ensure the interface JAR is also published:

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

* `core` contains the domain logic for the given application; ideally this is organised around a free algebra which makes `core` self-contained and fully testable without any kind of wire protocol. The module is primarily composed of pure functions (lifted into I/O actions where appropriate)

* `rpc-protocol` is a simple compilation unit that only contains the definition of the protocol (not the implementation)

* `rpc` contains the implementation of the aforementioned remotely protocol. In essence this is the meat of any wire<->domain mediation that needs to happen in the application; it exchanges wire representations (whatever they may be) for domain types that can be used as function arguments for the algebra exposed by `core`. This module *must depend on the `rpc-protocol` and `core` modules*.

* `project` is the usual SBT build information.

Once you have the layout configured, using remotely is just like using any other library within SBT; simply add the dependency to your `rpc-protocol` module:

```
resolvers += Resolver.bintrayRepo("oncue", "releases")

libraryDependencies += "oncue.remotely" %% "core" % "x.x.+"
```

### Protocol Definition

The first thing that *Remotely* needs is to define a "protocol". A protocol is essentially a definition of the runtime contracts this server should enforce on callers. Consider this example from `rpc-protocol/src/main/scala/protocol.scala`:

```scala
scala> import remotely._, codecs._
import remotely._
import codecs._

scala> object protocol {
     |   val definition = Protocol.empty
     |     .codec[Int]
     |     .specify1[Int, Int]("factorial")
     | }
defined module protocol
```

Protocols are the core of the remotely project. They represent the contract between the client and the server, and then define all the plumbing constrains needed to make that service work properly. The protocol object supports the following operations:

* `empty`: create a new, empty protocol as a starting point for building out a service contract.

* `codec`: require a `Codec` for the specified type. If the relevant `Codec` cannot be found, you will either have to import one or define your own accordingly. By default, *Remotely* comes with implementations for `String`, `Int` etc - the vast majority of default types you might need. You can however still define `Codec` implementations for any of your own structures as needed.

* `specify`n: define a function that the contract will have to implement. In our example, we want to expose a "factorial" function of one `Int` argument that return an `Int`; type parameters are types of function arguments followed by the return type of function.

### Server & Client Definition

*Remotely* makes use of compile-time macros to build out interfaces for server implementations and client objects. Because of limitations of the scala macro system, the use of the macros must be in a separate compilation unit (separate project or sub-project) then your protocol implementation. Now that your your service `Protocol` is defined in the `rpc-protocol` module, You can make a dependent `rpc` module which can access that protocol as a total value, enabling us to generate servers and clients. This `rpc` module will need to include the [Macro Paradise](http://docs.scala-lang.org/overviews/macros/paradise.html) compiler plugin, which you can do by adding the following to the build definition for the `rpc` project:


```
addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)

```

Unfortunately if you forget this step, compilation of the `rpc` module will succeed, however the macro will not run. Now we are ready to generate a remotely server. Here's an example:

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

The `GenServer` macro does require all the FQCN of all the inputs, so here we must use `oncue.svc.example.protocol.definition`. For example, using just `protocol.definition` after `import oncue.svc.example._` would *not* work, as in that case, the macro would then only see the AST: `protocol.definition` which doesn't have a value in the compiler context, but provided you organize your project in the manner described earlier in this document.

In a similar fashion, clients are also very simple. The difference here is that clients are fully complete, and do not require any implementation as the function arguments defined in the protocol are entirely known at compile time.

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

With everything defined, you can now make choices about how best to wire all the things together. For this getting started guide we'll focus on a simple implementation, but the detailed documentation on this site covers lots more information on endpoints, circuit breakers, monitoring, TLS configuration etc.

Here's the `main` for the server side:

```
package oncue.svc.example

import java.net.InetSocketAddress
import remotely._, codecs._

object Main {

  def main(args: Array[String]): Unit = {

    val address  = new InetSocketAddress("localhost", 8080)

    val service = new FactorialServer0

    val env = service.environment

    val startServer = env.serve(address)

    val shutdown = startServer.run

  }
}
```

This is super straightforward, but lets step through the values one by one.

* `address`: The network location the server process will bind to on the host machine.

* `service`: An instance of the server implementation (which in turn implements the macro-generated interface based on the protocol).

* `env`: For any given service implementation, an `Environment` can be derived from it. Unlike the protocol implementation itself, `Environment` is a concrete thing that can be bound and executed at runtime, where as `Protocol` is primarily a compile-time artifact.

* `startServer`: Calling `env.serve(...)` returns A Task that when run will start the server, binding the process to the specified address.

* `shutdown`: Calling `startServer.run` returns a new Task can be later used to shutdown the server, if necessary.

The client on the other hand is similar:

```
package oncue.svc.example

import scalaz.concurrent.Task
import java.net.InetSocketAddress
import remotely._, codecs._, transport.netty._

object Main {
  import Remote.implicits._

  def main(args: Array[String]): Unit = {

    val address  = new InetSocketAddress("localhost", 8080)

    val transport = NettyTransport.single(address)

    val endpoint = Endpoint.single(transport).run

    val f: Remote[Int] = FactorialClient.reduce(2 :: 4 :: 8 :: Nil)

	val task: Task[Int] = f.runWithoutContext(endpoint)

    // then at the edge of the world, run it and print to the console
    task.map(println(_)).runAsync(_ => ())
  }
}

```

Whilst `address` is the same value from the server, the typical case here of course is that the server and the client are not in the same source file so I've repeated it here for completeness. Let's explore the other values:

* `transport`: This is the underlying network transport which is responsible for transporting serialized requests from the client to the server, and the responses back to the client. Currently, Netty is the only implemented transport.

* `endpoint`: An `Endpoint` is an abstraction over callable service locations. Whilst the `Endpoint` object has a range of combinators, for this simple example we simply construct a fixed, static, single location. More information can be found in the [detailed documentation](manual).

* `f`: Application of the remote function reference. Here we pass in the arguments needed by the remote function, and at compile time you will be forced to ensure that you have a `Codec` for all the arguments supplied, and said arguments must be in scope within that compilation unit (i.e. if the remote service uses custom structures you'll need to ensure you import those accordingly). At this point *no request has actually been made over the wire*.

* `task`: In order to do something useful with the `Remote` instance its necessary to make a choice about what "context" this remote operation will be executed in. Again, this is covered specifically in the [detailed documentation](manual), but for now we shall elect to run without a context, by way of the `runWithoutContext` function. There still has been no network I/O occur; we have simply applied the function operation and transformed it into a scalaz `Task`.

Finally, the function does network I/O to talk to the server when the `Task` is executed (using the `runAsync` method here). You can learn more about the `Task` monad [in this blog post](http://timperrett.com/2014/07/20/scalaz-task-the-missing-documentation/).






