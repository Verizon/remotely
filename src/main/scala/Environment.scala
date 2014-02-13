package srpc

import scala.reflect.runtime.universe.TypeTag
import scala.collection.concurrent.TrieMap
import scalaz.concurrent.Task
import scalaz.Monad
import scodec.{Codec,Decoder,Encoder}

// could do dynamic lookup of encoder, decoder, using type tags

case class Environment(decoders: Map[String,Decoder[Nothing]],
                       encoders: Map[String,Encoder[Any]],
                       values: Map[String,Any]) {

  def encoder[A:TypeTag:Encoder]: Environment = {
    val name = Remote.toTag(implicitly[TypeTag[A]])
    this.copy(encoders = encoders + (name -> Encoder[A].asInstanceOf[Encoder[Any]]))
  }

  def decoder[A:TypeTag:Decoder]: Environment = {
    val name = Remote.toTag(implicitly[TypeTag[A]])
    this.copy(decoders = decoders + (name -> Decoder[A].asInstanceOf[Decoder[Nothing]]))
  }

  def codec[A](implicit T: TypeTag[A], C: Codec[A]): Environment = {
    import Codecs.{codecAsEncoder,codecAsDecoder}
    this.encoder[A].decoder[A]
  }

  /** Declare or update the value for the given name in this `Environment` */
  def update[A:TypeTag](name: String)(a: A): Environment = {
    val tag = Remote.nameToTag[A](name)
    this.copy(values = values + (tag -> a))
  }

  /**
   * Declare the value for the given name in this `Environment`,
   * or throw an error if the type-qualified name is already bound.
   */
  def declare[A:TypeTag](name: String)(a: A): Environment = {
    val tag = Remote.nameToTag[A](name)
    if (values.contains(tag)) sys.error("Environment already has declaration for: "+tag)
    else this.copy(values = values + (tag -> a))
  }
}

object Environment {
  def empty = Environment(Map(), Map(), Map())
}
