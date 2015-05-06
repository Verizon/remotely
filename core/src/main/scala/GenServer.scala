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

import scala.language.experimental.macros
import scala.annotation.StaticAnnotation
import scalaz.NonEmptyList

/**
 * Macro annotation that generates a server interface. Usage:
 * `@GenServer(remotely.Protocol.empty) abstract class MyServer`
 */
class GenServer(p: Protocol[_]) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro GenServer.impl
}

object GenServer extends MacrosCompatibility {
  import Signature._

  // Turns a `String` into a `TypeTree`.
  def parseType(c: Context)(s: String): c.universe.Tree = {
    import c.universe._
    val q"type T = $t" = c.parse(s"type T = $s")
    t
  }

  def liftSignature(c: Context)(s: Signature): c.universe.Tree = {
    import c.universe._
    val t: Tree = q"_root_.remotely.Signature(${s.name}, ${s.tag}, ${s.inTypes}, ${s.outType})"
    t
  }

  def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._
    import Flag._

    // Pull out the Protocol expression from the macro annotation
    // and evaluate it at compile-time.
    val p:Protocol[_] = c.prefix.tree match {
      case q"new $name($protocol)" =>
        c.eval(c.Expr[Protocol[_]](resetLocalAttrs(c)(q"{import remotely.codecs._; $protocol}")))
      case _ => c.abort(c.enclosingPosition, "GenServer must be used as an annotation.")
    }

    // Generates a method signature based on a method name and a `TypeTree`.
    def genSig(name: String, typ: c.Tree) =
      DefDef(Modifiers(DEFERRED), createTermName(c)(name),
             List(), List(),
             typ, EmptyTree)

    // Creates name/type pairs from the signatures in the protocol.
    val signatures = p.signatures.signatures.map { s =>
      val (n, t) = Signatures.split(s.tag)
      val typ = parseType(c)(Signatures.wrapResponse(t))
      (n, typ)
    }

    // Generates the method defs for the generated class.
    val sigDefs = signatures.collect { case (n,t) if n != "describe" =>
      genSig(n, t)
    }

    // Generates an expression that evaluates to the codecs specified by the protocol.
    val codecs =
      q"""${ p.codecs.keySet.toList.sorted.foldLeft(q"Codecs.empty":c.Tree)((dec, d) =>
        q"$dec.codec[${parseType(c)(d)}]"
      )}"""

    // Generate the actual abstract class with the environment and method defs generated above.
    val result = {
      annottees.map(_.tree).toList match {
        case q"abstract class $name extends ..$parents { ..$body }" :: Nil =>
          q"""
            abstract class $name extends ..$parents {
              import remotely.{Codecs,Environment,Response,Values}
              import remotely.codecs._

              def environment = Environment(
                $codecs,
                populateDeclarations(Values.empty)
              )

              ..$sigDefs

              def describe: Response[List[Signature]] = 
                Response.delay(${p.signatures.signatures.foldLeft[Tree](q"List.empty[Signature]")((e,s) => q"$e.::(${liftSignature(c)(s)})")})


             private def populateDeclarations(env: Values): Values =
                ${ signatures.foldLeft(q"env":c.Tree)((e,s) =>
                    q"$e.declare(${Literal(Constant(s._1))},${Ident(createTermName(c)(s._1))})"
                  )}

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
