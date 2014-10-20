package remotely

import scala.reflect.macros.Context
import scala.language.experimental.macros
import scala.annotation.StaticAnnotation

/**
 * Macro annotation that generates a client. Usage:
 * `@GenClient(remotely.Protocol.empty) object MyClient`
 */
class GenClient(sigs: Signatures) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro GenClient.impl
}

object GenClient {
  def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._
    import Flag._

    // Pull out the Signatures expression from the macro annotation
    // and evaluate it at compile-time.
    val s: Signatures = c.prefix.tree match {
      case q"new $name($sig)" =>
        c.eval(c.Expr[Signatures](c.resetAllAttrs(q"{import remotely.codecs._; $sig}")))
      case _ => c.abort(c.enclosingPosition, "GenClient must be used as an annotation.")
    }

    // Generate the val-defs that get inserted into the object declaration
    val signatures = s.signatures.toList.sorted.map { sig =>
      val (name, typ) = Signatures.split(sig)
      c.parse(s"""val $name = Remote.ref[$typ]("$name")""")
    }

    // Generate the actual client object, with the signature val-defs generated above
    val result = annottees.map(_.tree).toList match {
      case q"object $name extends ..$parents { ..$body }" :: Nil => q"""
        object $name extends ..$parents {
          import remotely.Remote
          ..$signatures
          ..$body
        }
      """
      case _ => c.abort(
        c.enclosingPosition,
        "GenClient must annotate an object declaration."
      )
    }
    c.Expr[Any](result)
  }

}
