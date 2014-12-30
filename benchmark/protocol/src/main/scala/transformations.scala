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

trait transformations {
  def fromSmallW(s: SmallW): Small = Small(s.alpha, s.omega)
  def toSmallW(s: Small): SmallW = SmallW(s.alpha, s.omega)

  def fromMediumW(m: MediumW): Medium = Medium(m.ay,
                                               m.bee,
                                               m.cee.map(fromSmallW),
                                               m.dee)

  def toMediumW(m: Medium): MediumW = MediumW(m.ay,
                                              m.bee,
                                              m.cee.map(toSmallW),
                                              m.dee)

  def fromLargeW(l: LargeW): Large = Large(l.one,
                                           l.two,
                                           l.three,
                                           l.four,
                                           l.five.map(fromMediumW),
                                           l.six.map(fromSmallW))

  def toLargeW(l: Large): LargeW = LargeW(l.one,
                                          l.two,
                                          l.three,
                                          l.four,
                                          l.five.map(toMediumW),
                                          l.six.map(toSmallW))



  def fromBigW(b: BigW): Big = Big(b.one)
  def toBigW(b: Big): BigW = BigW(b.one)
}

