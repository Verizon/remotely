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
