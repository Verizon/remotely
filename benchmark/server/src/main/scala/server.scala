package remotely
package example.benchmark
package server

import protocol._

@GenServer(remotely.example.benchmark.protocol.definition) abstract class BenchmarkServer
@GenClient(remotely.example.benchmark.protocol.definition.signatures) object BenchmarkClient

