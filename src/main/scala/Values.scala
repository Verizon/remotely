package srpc

import scala.reflect.runtime.universe.TypeTag

case class Values(values: Map[String,Any]) {

  /** Declare or update the value for the given name in this `Environment` */
  def update[A:TypeTag](name: String)(a: A): Values = {
    val tag = Remote.nameToTag[A](name)
    this.copy(values = values + (tag -> a))
  }

  /**
   * Declare the value for the given name in this `Environment`,
   * or throw an error if the type-qualified name is already bound.
   */
  def declare[A:TypeTag](name: String)(a: A): Values = {
    val tag = Remote.nameToTag[A](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> a))
  }

  def keySet = values.keySet

}

object Values {

  val empty = Values(Map())
}
