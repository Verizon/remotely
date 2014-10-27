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

// DEMO CUSTOM CODECS HERE


```


<a name="references"></a>

## Reference and Response

One of the interesting things with *Remotely* is that remote server functions are modeled as local functions using a "remote reference". That's quite an opaque statement, so let's illustrate it with an example:

```
import remotely._

Remote.ref[Int => Int]("factorial")
```

Notice how this is just a reference - it doesnt actually *do* anything. At this point we have told the system that here's an immutable handle to a function that *might* later be avalbile on an arbitrary endpoint, and the type of function being provided is `Int => Int` and its name is "factorial". This is interesting (and useful) because it entirely decouples the understanding about a given peice of functionality on the client side, and the system actor that will ultimatly fulfil that request. 

### Remote

### Response


<a name="endpoint"></a>

## Endpoints

TODO E

<a name="endpoint-circuit"></a>

### Circuit Breakers

TODO CB

<a name="endpoint-loadbalencing"></a>

### Load Balencing

TODO LB
