package remotely

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

  def generateClient(moduleName: String, pkg: String): String = {
    def emitValue(s: String) = {
      val (name,tname) = Signatures.split(s)
      s"""val $name = Remote.ref[$tname]("$name")"""
    }

    s"""
    |package $pkg
    |
    |import remotely.Remote
    |
    |object $moduleName {
    |  // This module contains code generated from a `remotely.Protocol`. Do not alter.
    |  ${signatures.toList.sorted.map(emitValue).mkString("\n\n  ")}
    |}
    |""".stripMargin
  }

  private[remotely] def generateServerTraitBody: String = {
    // converts from `A` to `Response[A]`, `A => B` to `A => Response[B]`,
    // `(A,B) => C` to `(A,B) => Response[C]`, etc, via string munging
    // NB: this regex is too simplistic to work for types like `A => (B => C)`,
    // but we don't have to worry about these types as a remote function cannot
    // return a function type in its result (as functions cannot be encoded and sent over wire!)
    val Arrow = "(.*)=>(.*)".r
    def wrapResponse(typename: String): String = typename match {
      case Arrow(l,r) => s"$l=> Response[${r.trim}]"
      case _ => s"Response[$typename]"
    }
    def emitSignature(s: String): String = {
      val (name, tname) = Signatures.split(s)
      s"def $name: ${wrapResponse(tname)}"
    }
    def emitDeclaration(s: String): String = {
      val (name, tname) = Signatures.split(s)
      s""".declare("$name", $name)"""
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

  private[remotely] def indent(by: String)(s: String): String =
    by + s.replace("\n", "\n" + by)
}
