package srpc

import scala.reflect.runtime.universe.TypeTag
import scodec.Decoder

case class Decoders(decoders: Map[String,Decoder[Any]]) {

  def decoder[A:TypeTag:Decoder]: Decoders = {
    val name = Remote.toTag(implicitly[TypeTag[A]])
    this.copy(decoders = decoders + (name -> Decoder[A].asInstanceOf[Decoder[Nothing]]))
  }

  def keySet = decoders.keySet

  def get(k: String): Option[Decoder[Any]] = decoders.get(k)
}

object Decoders {
  val empty = Decoders(Map())
}
