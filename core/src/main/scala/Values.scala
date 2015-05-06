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
  def apply(args: Any*): Response[Any]
}

object Value {
  def fromValue(a: Any) = new Value {
    def apply(args: Any*) = args.length match {
      case 0 => a.asInstanceOf[Response[Any]]
      case 1 => a.asInstanceOf[Any => Response[Any]](args(0))
      case 2 => a.asInstanceOf[(Any,Any) => Response[Any]](args(0), args(1))
      case 3 => a.asInstanceOf[(Any,Any,Any) => Response[Any]](args(0), args(1), args(2))
      case 4 => a.asInstanceOf[(Any, Any, Any, Any) => Response[Any]](args(0), args(1), args(2), args(3))
      case 5 => a.asInstanceOf[(Any, Any, Any, Any, Any) => Response[Any]](args(0), args(1), args(2), args(3), args(4))
      case 6 => a.asInstanceOf[(Any, Any, Any, Any, Any, Any) => Response[Any]](args(0), args(1), args(2), args(3), args(4), args(5))
      case n => Response.fail(new Exception("functions of arity " + n + " not supported"))
    }
  }
}

case class Values(values: Map[String,Value]) {

  /**
   * Declare the value for the given name in this `Environment`,
   * or throw an error if the type-qualified name is already bound.
   */
//  def declareStrict[A:TypeTag](name: String, a: A): Values = {
//    val tag = Remote.nameToTag[A](name)
//    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
//    else this.copy(values = values + (tag -> Value.fromValue(Response.now(a))))
//  }

  def declare[A:TypeTag](name: String, a: Response[A]): Values = {
    val tag = Remote.nameToTag[A](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.fromValue(a)))
  }

  def declare[A:TypeTag,B:TypeTag](name: String, f: A => Response[B]): Values = {
    val tag = Remote.nameToTag[A => B](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.fromValue(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag](name: String, f: (A,B) => Response[C]): Values = {
    val tag = Remote.nameToTag[(A,B) => C](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.fromValue(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag](name: String, f: (A,B,C) => Response[D]): Values = {
    val tag = Remote.nameToTag[(A,B,C) => D](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.fromValue(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag](name: String, f: (A,B,C,D) => Response[E]): Values = {
    val tag = Remote.nameToTag[(A,B,C,D) => E](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.fromValue(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag,F:TypeTag](name: String, f: (A,B,C,D,E) => Response[F]): Values = {
    val tag = Remote.nameToTag[(A,B,C,D,E) => F](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.fromValue(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag,F:TypeTag,G:TypeTag](name: String, f: (A,B,C,D,E,F) => Response[G]): Values = {
    val tag = Remote.nameToTag[(A,B,C,D,E,F) => G](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.fromValue(f)))
  }

  def keySet = values.keySet
}

object Values {

  val empty = Values(Map())
}
