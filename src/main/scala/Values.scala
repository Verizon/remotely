package remotely

import scala.reflect.runtime.universe.TypeTag
import scalaz.concurrent.Task

trait Value {
  def apply(a: Task[Any]*): Task[Any]
}

object Value {

  val T = Task.taskInstance

  /** Create a `Value` from a strict `A`. */
  private[remotely] def strict[A](a: A): Value = new Value {
    def apply(args: Task[Any]*): Task[Any] = Task.suspend { (args.length: @annotation.switch) match {
      case 0 => Task.now(a)
      case 1 => T.map(args(0))(a.asInstanceOf[Any => Any])
      case 2 => T.apply2(args(0),args(1))(a.asInstanceOf[(Any,Any) => Any])
      case 3 => T.apply3(args(0),args(1),args(2))(a.asInstanceOf[(Any,Any,Any) => Any])
      case 4 => T.apply4(args(0),args(1),args(2),args(3))(a.asInstanceOf[(Any,Any,Any,Any) => Any])
      case 5 => T.apply5(args(0),args(1),args(2),args(3),args(4))(a.asInstanceOf[(Any,Any,Any,Any,Any) => Any])
      case 6 => T.apply6(args(0),args(1),args(2),args(3),args(4),args(5))(a.asInstanceOf[(Any,Any,Any,Any,Any,Any) => Any])
      case n => Task.fail(new Exception("functions of arity " + n + " not supported"))
    }}
  }

  def strict0[A](a: A): Value = strict(a)
  def strict1[A,B](f: A => B): Value = strict(f)
  def strict2[A,B,C](f: (A,B) => C): Value = strict(f)
  def strict3[A,B,C,D](f: (A,B,C) => D): Value = strict(f)
  def strict4[A,B,C,D,E](f: (A,B,C,D) => E): Value = strict(f)
  def strict5[A,B,C,D,E,F](f: (A,B,C,D,E) => F): Value = strict(f)
  def strict6[A,B,C,D,E,F,G](f: (A,B,C,D,E,F) => G): Value = strict(f)

  /** Create an asynchronous value or function from the given `A`. */
  private[remotely] def async[A](a: A): Value = new Value {
    def apply(args: Task[Any]*): Task[Any] = Task.suspend { (args.length: @annotation.switch) match {
      case 0 => a.asInstanceOf[Task[Any]]
      case 1 => T.bind(args(0))(a.asInstanceOf[Any => Task[Any]])
      case 2 => T.join(T.apply2(args(0),args(1))(a.asInstanceOf[(Any,Any) => Task[Any]]))
      case 3 => T.join(T.apply3(args(0),args(1),args(2))(a.asInstanceOf[(Any,Any,Any) => Task[Any]]))
      case 4 => T.join(T.apply4(args(0),args(1),args(2),args(3))(a.asInstanceOf[(Any,Any,Any,Any) => Task[Any]]))
      case 5 => T.join(T.apply5(args(0),args(1),args(2),args(3),args(4))(a.asInstanceOf[(Any,Any,Any,Any,Any) => Task[Any]]))
      case 6 => T.join(T.apply6(args(0),args(1),args(2),args(3),args(4),args(5))(a.asInstanceOf[(Any,Any,Any,Any,Any,Any) => Task[Any]]))
      case n => Task.fail(new Exception("functions of arity " + n + " not supported"))
    }}
  }

  def async0[A](a: Task[A]): Value = async(a)
  def async1[A,B](f: A => Task[B]): Value = async(f)
  def async2[A,B,C](f: (A,B) => Task[C]): Value = async(f)
  def async3[A,B,C,D](f: (A,B,C) => Task[D]): Value = async(f)
  def async4[A,B,C,D,E](f: (A,B,C,D) => Task[E]): Value = async(f)
  def async5[A,B,C,D,E,F](f: (A,B,C,D,E) => Task[F]): Value = async(f)
  def async6[A,B,C,D,E,F,G](f: (A,B,C,D,E,F) => Task[G]): Value = async(f)
}

case class Values(values: Map[String,Value]) {

  /**
   * Declare the value for the given name in this `Environment`,
   * or throw an error if the type-qualified name is already bound.
   */
  def declareStrict[A:TypeTag](name: String)(a: A): Values = {
    val tag = Remote.nameToTag[A](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.strict(a)))
  }

  def declare0[A:TypeTag](name: String)(a: Task[A]): Values = {
    val tag = Remote.nameToTag[A](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.async0(a)))
  }

  def declare1[A:TypeTag,B:TypeTag](name: String)(f: A => Task[B]): Values = {
    val tag = Remote.nameToTag[A => B](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.async1(f)))
  }

  def declare2[A:TypeTag,B:TypeTag,C:TypeTag](name: String)(f: (A,B) => Task[C]): Values = {
    val tag = Remote.nameToTag[(A,B) => C](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.async2(f)))
  }

  def declare3[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag](name: String)(f: (A,B,C) => Task[D]): Values = {
    val tag = Remote.nameToTag[(A,B) => C](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.async3(f)))
  }

  def keySet = values.keySet
}

object Values {

  val empty = Values(Map())
}
