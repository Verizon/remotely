//: ----------------------------------------------------------------------------
//: Copyright (C) 2014 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------

package remotely
package examples
import scalaz._
import remotely.codecs._

// NB: The GenServer macro needs to receive the FQN of all types, or import them
// explicitly. The target of the macro needs to be an abstract class.
@GenServer(remotely.Protocol.empty.codec[Int].specify1("fac", Field.strict[Int]("in"), Field.strict[Int]("out")))
  abstract class FacServer

// The `GenClient` macro needs to receive the FQN of all types, or import them
// explicitly. The target needs to be an object declaration.
@GenClient(remotely.Protocol.empty.codec[Int].specify1[Int,Int]("fac", Field.strict[Int]("in"), Field.strict[Int]("out")).signatures)
  object FacClient

// TODO(ahjohannessen): @stew <- Compiling Foo fails somehow?
// @GenServer(remotely.Protocol.empty.codec[List[Int]]) abstract class Foo

object TestProtocol {

  // We can make a server
  lazy val facServer = new FacServer {
    val fac = (n: Int) => Response.now { (1 to n).product }
  }

  // We can get the environment out of a generated server:
  lazy val env = facServer.environment
}

