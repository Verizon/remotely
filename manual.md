---
layout: default
title:  "Manual"
section: "manual"
---

# Manual

*Remotely* is a sophisticated but simple system. There are a few core concepts and then a set of associated details. Some of these details are important to know, and others might just catch your interest. 

<a name="codecs"></a>

## Codecs

One of the most important elements of *Remotely* is its fast, lightweight serialisation system built atop [Scodec](https://github.com/scodec/scodec). This section reviews the default behavior that ships with *Remotely* and then explains how to extend these with your own custom codecs if needed.

By default, *Remotely* ships with the ability to serialise / deserialise the following "primitive" types:

* `A \/ B` (scalaz.\/, otherwise known as a disjunction)* `Array[Byte]`* `Boolean`* `Double`* `Either[A,B]`* `Float`* `IndexedSeq[A]`* `Int` (both 32 and 64)* `List[A]`* `Map[A,B]`* `Option[A]`* `Set[A]`* `SortedMap[A,B]`* `SortedSet[A]`* `String` (encoded with UTF8)* `Tuple2...7`
* `remotely.Response.Context`

Often these built-in defaults will be all you need, but there might be times where it feels like it would be more appropriate do provide a "wire type" (that is, a datatype that represents the external wire API - **NOT** a data type that forms part of your core domain model). Typically this happens when you have a convoluted structure or a very "stringly-typed" interface (e.g. `Map[String, Map[String, Int]]` - who knows what on earth the author intended here!). In this cases, implementing custom codecs for your protocol seems attractive, and fortunatly its really simple to do:

```
package oncue.example

import remotely.codecs._
import scodec.{Codec,codecs => C}
import scodec.bits.BitVector
import java.nio.charset.Charset

case class ComponentW(kind: String, metadata: Map[String,String])

package object myapp {

  implicit val charset: Charset = Charset.forName("UTF-8")

  implicit val componentCodec: Codec[ComponentW] =
    (utf8 ~~ map[String,String]).pxmap(
      ComponentW.apply,
      ComponentW.unapply
    )
}

```

In this example, `ComponentW` is part of our wire protocol definition, and provides some application-specific semantic that is meaningful for API consumers (assuming the fields `kind` and `metadata` have "meaning" together). To make this item serializable, we simply need to tell scodec about the *shape* of the structure (in this case, `String` and `Map[String,String]`) and then supply a function `Shape => A` and then `A => Option[(Shape)]`. At runtime *Remotely* will use this codec to take the bytes from the wire and convert it into the `ComponentW` datatype using the defined shape and the associated fucntions.


```
// TODO: add more sophisticated examples?

```

<a name="references"></a>

## Remotes

One of the interesting design points with *Remotely* is that remote server functions are modeled as local functions using a "remote reference". That's quite an opaque statement, so let's illustrate it with an example:

```
import remotely._

Remote.ref[Int => Int]("factorial")
```

Notice how this is *just a reference* - it doesnt actually *do* anything. At this point we have told the system that here's an immutable handle to a function that *might* later be avalbile on an arbitrary endpoint, and the type of function being provided is `Int => Int` and its name is "factorial". This is interesting (and useful) because it entirely decouples the understanding about a given peice of functionality on the client side, and the system actor that will ultimatly fulfil that request. *Remotely* clients will automatically model the server functions in this manner, so lets take a look at actually caling one of these functions:

```
scala> FactorialClient.factorial(1)
<console>:11: error: type mismatch;
 found   : Int(1)
 required: remotely.Remote[Int]
              FactorialClient.factorial(1)
``` 

That didnt go as planned! As it turns out, `Remote` function references can only be applied using values that have been explicitly lifted into a `Remote`  context, and *Remotely* comes with several convenient combinators to do that:

* `Remote.local`: given a total value, simply lift it directly into a `Remote` instance. This is the most simplistic form of the `Remote` API, and is useful in many cases; especially for testing.

* `Remote.async`: given a value `A` that is computed from some `Task[A]`, execute the `Task` and turn the value into a `Remote[A]`. For example, perhaps you lookup a value from a database and want to call a remote service with that given value, this is a convenient combinator for just such a case.

* `Remote.response`: Given a `Response` from another remote function execution (more on `Remote` shortly), use it as the input to this remote function. This is incredibly useful for chaining calls to dependant systems.

You can choose to either use these functions directly, or have them implciitly applied by adding the following implicit conversion:

```
import remotely._, codecs._, Remote.implicits._
```

With this in scope, these functions will be automatically applied. One word of caution: you must ensure that you have a `Codec` in scope for whatever `A` you are trying to convert to a `Remote` value.

<a name="endpoint"></a>

## Endpoints

Now that you have a `Remote` function and you know how to apply arguments (applying the function inside the `Remote` monad), we need to explore the next important primitive in *Remotely*: `Endpoint`. An `Endpoint` models the network locaiton of a specific server on a specific TCP port which can service function calls. Internally, `Endpoint` instances are modeled as a stream of `Endpoint`; doing this allows for a range of flexiblity around circuit breaking and load balencing. Users can either embrace this `Process[Task, Endpoint.Connection]` directly, or use some of the convenience functions outlined below:

* `Endpoint.empty`: create an empty endpoint, with no reachable locations in the stream.

* `Endpoint.single`: Create an endpoint representing a single IP:PORT location of a *Remotely* service. 

* `Endpoint.singleSSL`: Does the same as `single`, with the addition of using transport layer security (specifically, TLS1.2) 

Using these basic combinators, we can now execute the `Remote` against a given endpoint. In order to do this, you have to elect what "context" the remote call will carry with it. 

<a name="execution-context"></a>

### Execution Context

A `Context` is essentially a primitive data type that allows a given function invokation to carry along some metadata. When designing *Remotely*, we envisinged the following use cases:

* *Transitive Request Graphing*: in large systems, it becomes extreamly useful to understand which instances of any given service is actually taking traffic and what the call graph actually is from a given originating caller. In this frame, `Context` comes with a stack of request IDs which are generated on each roundtrip, and if service A calls service B, the caller of A will recive a stack of IDs that detnote the call all the way to B. 

* *Experimentation*: 


With these basic functions defined, we can build some higher level logic to gain resiliance and scability features which we cover in the next few sections. 




<a name="circuit-breakers"></a>

### Circuit Breakers


<a name="load-balencing"></a>

### Load Balencing

`Endpoint.roundRobin`





<a name="responses"></a>

### Responses
