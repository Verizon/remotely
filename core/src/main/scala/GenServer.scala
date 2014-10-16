package remotely

import scala.reflect.macros.Context
import scala.language.experimental.macros
import scala.annotation.StaticAnnotation


class GenServer(p: Protocol) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro GenServer.impl
}

object GenServer {
  def parseType(c: Context)(s: String): c.universe.Tree = {
    import c.universe._
    val q"type T = $t" = c.parse(s"type T = $s")
    t
  }

  def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._
    import Flag._
    val p:Protocol = c.prefix.tree match {
      case q"new $name($protocol)" =>
        c.eval(c.Expr[Protocol](c.resetAllAttrs(q"{import remotely.codecs._; $protocol}")))
      case _ => c.abort(c.enclosingPosition, "GenServer must be used as an annotation.")
    }

    def genSig(name: String, typ: c.Tree) =
      DefDef(Modifiers(DEFERRED), newTermName(name),
             List(), List(),
             typ, EmptyTree)

    val signatures = p.signatures.signatures.map { s =>
      val (n, t) = Signatures.split(s)
      val typ = parseType(c)(Signatures.wrapResponse(t))
      (n, typ)
    }

    val sigDefs = signatures.map { s =>
      val (n, t) = s
      genSig(n, t)
    }

    val codecs =
      q"""${ p.codecs.keySet.toList.sorted.foldLeft(q"Codecs.empty":c.Tree)((c, d) =>
        q"$c.codec[${Ident(newTypeName(d))}]"
      )}"""

    val result = {
      annottees.map(_.tree).toList match {
        case q"abstract class $name extends ..$parents { ..$body }" :: Nil =>
          q"""
            abstract class $name extends ..$parents {
              import remotely.{Codecs,Environment,Response,Values}
              import remotely.codecs._

              def environment: Environment = Environment(
                $codecs,
                populateDeclarations(Values.empty)
              )

              ..$sigDefs

              private def populateDeclarations(env: Values): Values =
                ${ signatures.foldLeft(q"env":c.Tree)((e,s) =>
                  q"$e.declare(${Literal(Constant(s._1))},${Ident(newTermName(s._1))})") }

              ..$body
            }
          """
        case _ => c.abort(
          c.enclosingPosition,
          "GenServer must annotate an abstract class declaration."
        )
      }
    }
    c.Expr[Any](result)
  }
}
