---
layout: default
title:  "Home"
section: "home"
---

# Introduction

Remotely is an elegant, reasonable machine communication system for functional programmers. Remotely is fast, lightweight and models network operations as explicit monadic computations.

## Rationale

Before talking about how to *use* Remotely, its probably worth discussing why it is we felt the need to make this project in the first place. For large distributed service platforms there is typically a large degree of inter-service communication that happens internally before returning control to the caller. In this scenario, several factors become really important: 

* **Productivity**. Whilst much progress has been made with the widely used HTTP protocol and there are a myriad of serialisation technologies commonly used for services (e.g. JSON, XML etc), in larger teams one can typically observe development time being spent on transforming `AST => A` and `A => AST`, where the AST is being used to represent the wire content (e.g. `JValue` from lift-json or similar). As these ASTs typically represent some kind of semi-structured data, many users have to then waste time traversing all these different JSON structures (with either none or extremely minimal reuse) to get the typed value they care about. In *Remotely* we have tried to address this by providing a generic means to serialise any `A` over the wire. This immediately removes the need for traversing various types of AST to extract the needed fields as the serialisation / deserialization code is highly composable (thanks to [scodec](https://github.com/scodec/scodec))

* **Safety**. Something that is distinctly missing from HTTP+JSON services is the ability to know - when moving between revisions of a dependant API - is if the given API is compatible or not with the existing mechanism of calling that service. Typically this kind of meta information ends up being encoded in a version number, or some other out-of-band knowledge. This often results in runtime failures and incompatibility between services unless exceptional care is taken by the service owner not to break their API in any way. Using *Remotely* we build the protocols just like any other compile-time artifact; said artefacts are then published to Nexus and depended upon as build-time contracts. With this base it is then easy to build all runtime dependant services as downstream jobs during the build phase, which gives you a build-time safety check, enabling engineers to get early visibility about compatibility within their service API (incompatibility in an API will result in a build-time failure).

* **Reuse**. In most typed-protocol definitions there is a low degree of reuse because the serialisation code does not compose. An example of this would be Thrift's protocol definitions: the definition contains all structures and values used by the client and server, and the entire world of needed files is generated at build time, even if that exact same structure (e.g tuples) is used by an adjacent service. Within *Remotely* we wanted to avoid this nasty code-generation step and instead rely on highly composable structures with their associated combinators to get the granularity and level of reuse we wanted.

## Getting Started

Using remotely is straight-forward, and getting started on a new project could not be simpler!

### Dependency Information

Remotely has the following dependencies:

* `Scalaz 7.0.6`
* `Scodec 1.1.3`
* `Akka I/O 2.2.4`
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

* `protocol` is a simple compilation unit that only contains the definition of the protocol (not the implementation) - more on this in the XXXX section.

* `rpc` contains the implementation of the aforementioned remotely protocol. In essence this is the meat of any wire<->domain mediation that needs to happen in the application; it exchanges wire representations (whatever they may be) for domain types that can be used as function arguments for the algebra exposed by `core`. This module *must depend on the `rpc` and `core` modules*.

* `project` is the usual SBT build information.

Once you have the layout configured, using remotely is just like using any other library within SBT; simply add the dependency to your `protocol` module:

```
libraryDependencies += "oncue.svc.remotely" %% "core" % "x.x.+"
```
(check for the latest release by [looking on the nexus](http://nexus.svc.oncue.com/nexus/content/repositories/releases/oncue/svc/remotely/core_2.10/))

### Protocol Definition



