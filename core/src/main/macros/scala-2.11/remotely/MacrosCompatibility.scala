//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
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

trait MacrosCompatibility {
  type Context = scala.reflect.macros.blackbox.Context

  def getDeclarations(c: Context)(tpe: c.universe.Type): c.universe.MemberScope =
    tpe.decls

  def getParameterLists(c: Context)(method: c.universe.MethodSymbol): List[List[c.universe.Symbol]] =
    method.paramLists

  def getDeclaration(c: Context)(tpe: c.universe.Type, name: c.universe.Name): c.universe.Symbol =
    tpe.decl(name)

  def createTermName(c: Context)(name: String): c.universe.TermName =
    c.universe.TermName(name)

  def createTypeName(c: Context)(name: String): c.universe.TypeName =
    c.universe.TypeName(name)

  def resetLocalAttrs(c: Context)(tree: c.Tree): c.Tree =
    c.untypecheck(tree)

  def getTermNames(c: Context): c.universe.TermNamesApi =
    c.universe.termNames

  def companionTpe(c: Context)(tpe: c.universe.Type): c.universe.Symbol =
    tpe.typeSymbol.companion
}