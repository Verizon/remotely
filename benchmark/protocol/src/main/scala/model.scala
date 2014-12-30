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
package example.benchmark

import collection.immutable.IndexedSeq

case class Large(one: Int,
                 two: List[String],
                 three: String,
                 four: Map[String, String],
                 five: List[Medium],
                 six: IndexedSeq[Small])

case class Medium(ay: Int,
                  bee: String,
                  cee: List[Small],
                  dee: Option[Int])

case class Small(alpha: Map[String,String],
                 omega: List[String])

case class Big(one: Int)
