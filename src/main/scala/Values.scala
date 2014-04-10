package remotely

import scala.reflect.runtime.universe.TypeTag
import scalaz.Monad

trait Value {
  def apply(a: Response[Any]*): Response[Any]
}

object Value {

  val R = Monad[Response]

  /**
   * Hilariously-named type which provides evidence that `A` can be converted
   * to a `Value`, used as a constraint when creating values.
   */
  trait Valueable[-A] { def apply(a: A): Value }

  object Valueable {
    implicit def async0[A] = new Valueable[Response[A]] {
      def apply(t: Response[A]) = new Value {
        def apply(args: Response[Any]*): Response[Any] =
          if (args.isEmpty) t
          else Response.fail(new Exception("non-function applied to arguments " + args))
      }
    }
    implicit def async1[A,B] = new Valueable[A => Response[B]] {
      def apply(f: A => Response[B]) = new Value {
        def apply(args: Response[Any]*): Response[Any] = args match {
          case Seq(x1) => R.bind(x1)(f.asInstanceOf[Any => Response[Any]])
          case _ => Response.fail(new Exception("function1 applied to incorrect number of args: " + args))
        }
      }
    }
    implicit def async2[A,B,C] = new Valueable[(A,B) => Response[C]] {
      def apply(f: (A,B) => Response[C]) = new Value {
        def apply(args: Response[Any]*): Response[Any] = args match {
          case Seq(x1,x2) => R.join(R.apply2(x1,x2)(f.asInstanceOf[(Any,Any) => Response[Any]]))
          case _ => Response.fail(new Exception("function2 applied to incorrect number of args: " + args))
        }
      }
    }
    implicit def async3[A,B,C,D] = new Valueable[(A,B,C) => Response[D]] {
      def apply(f: (A,B,C) => Response[D]) = new Value {
        def apply(args: Response[Any]*): Response[Any] = args match {
          case Seq(x1,x2,x3) => R.join(R.apply3(x1,x2,x3)(f.asInstanceOf[(Any,Any,Any) => Response[Any]]))
          case _ => Response.fail(new Exception("function3 applied to incorrect number of args: " + args))
        }
      }
    }
    implicit def async4[A,B,C,D,E] = new Valueable[(A,B,C,D) => Response[E]] {
      def apply(f: (A,B,C,D) => Response[E]) = new Value {
        def apply(args: Response[Any]*): Response[Any] = args match {
          case Seq(x1,x2,x3,x4) => R.join(R.apply4(x1,x2,x3,x4)(f.asInstanceOf[(Any,Any,Any,Any) => Response[Any]]))
          case _ => Response.fail(new Exception("function4 applied to incorrect number of args: " + args))
        }
      }
    }
    implicit def async5[A,B,C,D,E,F] = new Valueable[(A,B,C,D,E) => Response[F]] {
      def apply(f: (A,B,C,D,E) => Response[F]) = new Value {
        def apply(args: Response[Any]*): Response[Any] = args match {
          case Seq(x1,x2,x3,x4,x5) => R.join(R.apply5(x1,x2,x3,x4,x5)(f.asInstanceOf[(Any,Any,Any,Any,Any) => Response[Any]]))
          case _ => Response.fail(new Exception("function5 applied to incorrect number of args: " + args))
        }
      }
    }
    implicit def async6[A,B,C,D,E,F,G] = new Valueable[(A,B,C,D,E,F) => Response[G]] {
      def apply(f: (A,B,C,D,E,F) => Response[G]) = new Value {
        def apply(args: Response[Any]*): Response[Any] = args match {
          case Seq(x1,x2,x3,x4,x5,x6) => R.join(R.apply6(x1,x2,x3,x4,x5,x6)(f.asInstanceOf[(Any,Any,Any,Any,Any,Any) => Response[Any]]))
          case _ => Response.fail(new Exception("function6 applied to incorrect number of args: " + args))
        }
      }
    }
  }

  /** Create a `Value` from a strict `A`. */
  private[remotely] def strict[A](a: A): Value = new Value {
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

  /** Convert `A` to a `Value`. */
  def async[A](a: A)(implicit A: Valueable[A]): Value = A(a)

  def strict0[A](a: A): Value = strict(a)
  def strict1[A,B](f: A => B): Value = strict(f)
  def strict2[A,B,C](f: (A,B) => C): Value = strict(f)
  def strict3[A,B,C,D](f: (A,B,C) => D): Value = strict(f)
  def strict4[A,B,C,D,E](f: (A,B,C,D) => E): Value = strict(f)
  def strict5[A,B,C,D,E,F](f: (A,B,C,D,E) => F): Value = strict(f)
  def strict6[A,B,C,D,E,F,G](f: (A,B,C,D,E,F) => G): Value = strict(f)

  def async0[A](a: Response[A]): Value = async(a)
  def async1[A,B](f: A => Response[B]): Value = async(f)
  def async2[A,B,C](f: (A,B) => Response[C]): Value = async(f)
  def async3[A,B,C,D](f: (A,B,C) => Response[D]): Value = async(f)
  def async4[A,B,C,D,E](f: (A,B,C,D) => Response[E]): Value = async(f)
  def async5[A,B,C,D,E,F](f: (A,B,C,D,E) => Response[F]): Value = async(f)
  def async6[A,B,C,D,E,F,G](f: (A,B,C,D,E,F) => Response[G]): Value = async(f)
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
    else this.copy(values = values + (tag -> Value.async0(a)))
  }

  def declare[A:TypeTag,B:TypeTag](name: String, f: A => Response[B]): Values = {
    val tag = Remote.nameToTag[A => B](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.async1(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag](name: String, f: (A,B) => Response[C]): Values = {
    val tag = Remote.nameToTag[(A,B) => C](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.async2(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag](name: String, f: (A,B,C) => Response[D]): Values = {
    val tag = Remote.nameToTag[(A,B,C) => D](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.async3(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag](name: String, f: (A,B,C,D) => Response[E]): Values = {
    val tag = Remote.nameToTag[(A,B,C,D) => E](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.async4(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag,F:TypeTag](name: String, f: (A,B,C,D,E) => Response[F]): Values = {
    val tag = Remote.nameToTag[(A,B,C,D,E) => F](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.async5(f)))
  }

  def declare[A:TypeTag,B:TypeTag,C:TypeTag,D:TypeTag,E:TypeTag,F:TypeTag,G:TypeTag](name: String, f: (A,B,C,D,E,F) => Response[G]): Values = {
    val tag = Remote.nameToTag[(A,B,C,D,E,F) => G](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> Value.async6(f)))
  }

  def keySet = values.keySet
}

object Values {

  val empty = Values(Map())
}
