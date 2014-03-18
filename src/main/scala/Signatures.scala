package srpc

import scala.reflect.runtime.universe.TypeTag

case class Signatures(signatures: Set[String]) {

  def specify[A:TypeTag](name: String): Signatures =
    Signatures(signatures + Remote.nameToTag[A](name))

  def specify1[A:TypeTag,B:TypeTag](name: String): Signatures =
    specify[A => B](name)

  def specify2[A:TypeTag,B:TypeTag,C:TypeTag](name: String): Signatures =
    specify[(A,B) => C](name)

  def specify3[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag](name: String): Signatures =
    specify[(A,B,C) => D](name)

  def specify4[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag](name: String): Signatures =
    specify[(A,B,C,D) => E](name)

  def generateClient(moduleName: String): String = {
    def emitValue(s: String) = {
      val parts = s.split(':').toIndexedSeq
      val name = parts.init.mkString(":").trim
      s"""val $name = Remote.ref[${parts.last.trim}]("$name")"""
    }

    s"""
    |import srpc.Remote
    |
    |object $moduleName {
    |  ${signatures.toList.sorted.map(emitValue).mkString("\n\n  ")}
    |}
    |""".stripMargin
  }
}

object Signatures {
  val empty = Signatures(Set())
}
