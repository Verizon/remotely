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

/**
 * Macro annotation that generates a client. Usage:
 * `@GenClient(remotely.Protocol.empty) object MyClient`
 */
class GenClient(sigs: Signatures) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro GenClient.impl
}

object GenClient extends MacrosCompatibility {
  /**
    * this just allows us to put a $signature into a quasi-quote.
    * implemented this way instead of by providing Liftable[Signature]
    * only because I gave up on trying to figure out the complex cake
    * of path-dependant types which is the current reflection api.
    */
  def liftSignature(c: Context)(signature: Signature): c.universe.Tree = {
    import c.universe._
    val s = signature
    val t: Tree = q"_root_.remotely.Signature(${s.name}, ${s.tag}, ${s.inTypes}, ${s.outType}, ${s.isStream})"
    t
  }

  def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._
    import Flag._

    // Pull out the Signatures expression from the macro annotation
    // and evaluate it at compile-time.
    val s: Signatures = c.prefix.tree match {
      case q"new $name($sig)" =>
        c.eval(c.Expr[Signatures](resetLocalAttrs(c)(q"{import remotely.codecs._; $sig}")))
      case _ => c.abort(c.enclosingPosition, "GenClient must be used as an annotation.")
    }

    // Generate the val-defs that get inserted into the object declaration
    val signatures : Set[Tree] = s.signatures.map { sig =>
        val (name, typ) = Signatures.split(sig.tag)
        c.parse(s"""val $name = Remote.ref[$typ]("$name")""")
    }


    // Generate a Set[Signature] of the function signatures we are
    // expecting any server to support. This will be baked into the
    // generated client as an expectedSignatures val.
    val esSet = {
      val sigs = s.signatures.map { liftSignature(c)(_) }
      c.Expr[Set[Signature]](q"Set[Signature]( ..${sigs.toList} )")
    }

    // Generate the actual client object, with the signature val-defs generated above
    val result = annottees.map(_.tree).toList match {
      case q"object $name extends ..$parents { ..$body }" :: Nil =>
        q"""
        object $name extends ..$parents {
          import remotely.Remote
          ..$signatures
          ..$body
          val expectedSignatures = ${esSet}
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
