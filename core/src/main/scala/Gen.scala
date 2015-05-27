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

private[remotely] object Gen extends MacrosCompatibility {
  /**
   * this just allows us to put a $signature into a quasi-quote.
   * implemented this way instead of by providing Liftable[Signature]
   * only because I gave up on trying to figure out the complex cake
   * of path-dependant types which is the current reflection api.
   */
  def liftSignature(c: Context)(signature: Signature): c.universe.Tree = {
    import c.universe._
    val s = signature
    val t: Tree = q"_root_.remotely.Signature(${s.name}, List(..${s.params.map(liftField(c)(_))}), ${liftType(c)(s.out)})"
    t
  }

  def liftField(c: Context)(field: Field[Any]): c.universe.Tree = {
    import c.universe._
    q"_root_.remotely.Field(${field.name}, ${liftType(c)(field.type_)})"
  }

  def liftType(c: Context)(type_ : Type[Any]): c.universe.Tree = {
    import c.universe._
    q"_root_.remotely.Type(${type_.name}, ${type_.isStream})"
  }
}
