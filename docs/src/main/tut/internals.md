---
layout: default
title:  "Internals"
section: "internals"
---

# Internals

TODO

<a name="serialization"></a>

## Serialization

### Float

Floats are encoded into 32 bits according to the [IEEE 754 floating-point single precision format](http://en.wikipedia.org/wiki/Single-precision_floating-point_format#IEEE_754_single-precision_binary_floating-point_format:_binary32)

### Double

Doubles are encoded into 64 bits according to the [IEEE 754 floating-point double precision format)[http://en.wikipedia.org/wiki/Double-precision_floating-point_format#IEEE_754_double-precision_binary_floating-point_format:_binary64] 

### Int

Ints are encoded as either 32 or 64 bit [2s compliment](http://en.wikipedia.org/wiki/Two's_complement), [big-endian](http://en.wikipedia.org/wiki/Endianness) format

### Boolean

Booleans are encoded as a single bit, 1 for true and 0 for false.

## Array[Byte]

Byte arrays are stored as a 32-bit signed integer representing the number of bytes, followed by the actual bytes.

### String

Strings are [UTF-8 encoded](http://en.wikipedia.org/wiki/UTF-8) into an Array of bytes, then serialized as Array[Byte] above

### Sequences: List[A], Seq[A], IndexedSeq[A], Set[A], SortedSet[A]

Sequence like structures are all stored as a 32-bit signed integer representing the number of items in the collection followed by the bytes for each member of the collection

### Option[A]

Optional values are represented by a single bit indicating if a byte encoding of the value follows.

### Tuple2..7

Tuples are represented by a concatenation of the byte representation of each tuple member.

### Disjunctions: A \/ B, Either[A,B]

Disjunctions are represented as a single bit discrimnator which indicates if the following bytes represent an A value or a B value. A zero bit indicates that A bytes follow, and a one bit indicates that B bytes follow

### Map[A,B] / SortedMap[A,B]

Maps are encoded by converting them first to a Sequence of Tuple2 values (representing Key followed by Value), which is then encoded as an above List[(A,B)] would be.

### Execution Context
- See the [Documentation about Excution Conext](/manual.html#execution-context]) for information about the Execution Context. 

An execution context is serialized as a Map[String,String] followed by a List[UUID] where uuid is encoded as 128 bits [as documented here](https://docs.oracle.com/javase/7/docs/api/java/util/UUID.html)

<a name="networking"></a>

## Networking

TODO

### Akka I/O

Currently the network I/O in remotely is currently all plumbed through [Akka I/O](http://doc.akka.io/docs/akka/snapshot/scala/io.html) internally.

TODO

### KeepAlive