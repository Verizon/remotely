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
      val (name,tname) = Signatures.split(s)
      s"""val $name = Remote.ref[$tname]("$name")"""
    }

    s"""
    |import srpc.Remote
    |
    |object $moduleName {
    |  // This module contains code generated from a `srpc.Protocol`. Do not alter.
    |  ${signatures.toList.sorted.map(emitValue).mkString("\n\n  ")}
    |}
    |""".stripMargin
  }

  private[srpc] def generateServerTraitBody: String = {
    def emitSignature(s: String): String = {
      val (name, tname) = Signatures.split(s)
      s"def $name: $tname"
    }
    def emitDeclaration(s: String): String = {
      val (name, tname) = Signatures.split(s)
      s""".declare[$tname]("$name") { $name }"""
    }

    signatures.map(emitSignature).mkString("\n\n") + "\n\n" +
    "private def populateDeclarations(env: Values): Values = env\n" +
    Signatures.indent("  ") { signatures.map(emitDeclaration).mkString("\n") }
  }

  def pretty: String = "Signatures.empty\n" +
    signatures.map(s => Signatures.split(s) match { case (name,tname) =>
      s"""  .specify[$tname]("$name")"""
    }).mkString("\n")
}

object Signatures {
  val empty = Signatures(Set())

  // converts "sum: List[Int] => Int" to ("sum", "List[Int] => Int")
  private def split(typedName: String): (String,String) = {
    val parts = typedName.split(':').toIndexedSeq
    val name = parts.init.mkString(":").trim
    val typename = parts.last.trim
    (name, typename)
  }

  private[srpc] def indent(by: String)(s: String): String =
    by + s.replace("\n", "\n" + by)
}
