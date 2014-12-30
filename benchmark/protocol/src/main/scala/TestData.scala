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

trait TestData {
  val sm: Small = Small((for(i <- 1 to 10) yield i.toString -> i.toString).toMap, (for(i <- 1 to 10) yield i.toString).toList)

  val medIn: Medium = Medium(1, (0 to 200).map(_ => "a").mkString, List.fill(10)(sm), Some(1))

  val largeIn: Large = Large(1, List("asdf", "qwer", "qwer","ldsfdfsaj","aksldjfsdfkdfjasdfpoweurpaasdflsdkfjsllslosdfiuasdpoaisudpfidsaf"), (1 to 1000).map((x:Int) => "a").mkString, (for(i <- 1 to 20) yield i.toString -> i.toString).toMap,List.fill(10)(medIn), Vector.fill(10)(sm))

  val bigIn: Big = Big(1)

}
