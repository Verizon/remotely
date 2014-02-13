package srpc


object Service {

}

object ServiceExample {

  import Remoteable._
  import Codecs._

  val fac: Remote[Int => Int] = ???
  val gcd: Remote[(Int,Int) => Int] = ???

  val ar = fac(9)
  val ar1 = gcd(1, 2)
  val ar2: Remote[Int] = ar
}
