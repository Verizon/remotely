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

import scala.reflect.runtime.universe.TypeTag
import scalaz.Monad

trait Value {
  def apply(a: Response[Any]*): Response[Any]
}

object Value {

  val R = Monad[Response]

  /** Create a `Value` from a strict `A`. */
  private[remotely] def strict(a: Any): Value = new Value {
    def apply(args: Response[Any]*): Response[Any] = Response.suspend { (args.length: @annotation.switch) match {
      case 0 => Response.now(a)
      case 1 => R.map(args(0))(a.asInstanceOf[Any => Any])
      case 2 => R.apply2(args(0),args(1))(a.asInstanceOf[(Any,Any) => Any])
      case 3 => R.apply3(args(0),args(1),args(2))(a.asInstanceOf[(Any,Any,Any) => Any])
      case 4 => R.apply4(args(0),args(1),args(2),args(3))(a.asInstanceOf[(Any,Any,Any,Any) => Any])
      case 5 => R.apply5(args(0),args(1),args(2),args(3),args(4))(a.asInstanceOf[(Any,Any,Any,Any,Any) => Any])
      case 6 => R.apply6(args(0),args(1),args(2),args(3),args(4),args(5))(a.asInstanceOf[(Any,Any,Any,Any,Any,Any) => Any])
      case n => Response.fail(new Exception("functions of arity " + n + " not supported"))
    }}
  }

  private[remotely] def response(a: Any): Value = new Value {
    def apply(args: Response[Any]*): Response[Any] = args.length match {
      case 0 => a.asInstanceOf[Response[Any]]
      case 1 => R.bind(args(0))(a.asInstanceOf[Any => Response[Any]])
      case 2 => R.join(R.apply2(args(0), args(1))(a.asInstanceOf[(Any, Any) => Response[Any]]))
      case 3 => R.join(R.apply3(args(0), args(1), args(2))(a.asInstanceOf[(Any, Any, Any) => Response[Any]]))
      case 4 => R.join(R.apply4(args(0), args(1), args(2), args(3))(a.asInstanceOf[(Any, Any, Any, Any) => Response[Any]]))
      case 5 => R.join(R.apply5(args(0), args(1), args(2), args(3), args(4))(a.asInstanceOf[(Any, Any, Any, Any, Any) => Response[Any]]))
      case 6 => R.join(R.apply6(args(0), args(1), args(2), args(3), args(4), args(5))(a.asInstanceOf[(Any, Any, Any, Any, Any, Any) => Response[Any]]))
      case n => throw new AssertionError("We do not provide functions to create Values of higher arity. Execution should never get here")
    }
  }
}

case class Values(values: Map[String,Value]) {

  /**
   * Declare the value for the given name in this `Environment`,
   * or throw an error if the type-qualified name is already bound.
   */
  def declareStrict[A:TypeTag](name: String, a: A): Values = {
    val tag = Remote.nameToTag[A](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.strict(a)))
  }

  def declare[A:TypeTag](name: String, a: Response[A]): Values = {
    val tag = Remote.nameToTag[A](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.response(a)))
  }

  def declare[A:TypeTag,B:TypeTag](name: String, f: A => Response[B]): Values = {
    val tag = Remote.nameToTag[A => B](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.response(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag](name: String, f: (A,B) => Response[C]): Values = {
    val tag = Remote.nameToTag[(A,B) => C](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.response(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag](name: String, f: (A,B,C) => Response[D]): Values = {
    val tag = Remote.nameToTag[(A,B,C) => D](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.response(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag](name: String, f: (A,B,C,D) => Response[E]): Values = {
    val tag = Remote.nameToTag[(A,B,C,D) => E](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.response(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag,F:TypeTag](name: String, f: (A,B,C,D,E) => Response[F]): Values = {
    val tag = Remote.nameToTag[(A,B,C,D,E) => F](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.response(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag,F:TypeTag,G:TypeTag](name: String, f: (A,B,C,D,E,F) => Response[G]): Values = {
    val tag = Remote.nameToTag[(A,B,C,D,E,F) => G](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.response(f)))
  }

  def keySet = values.keySet
}

object Values {

  val empty = Values(Map())
}
