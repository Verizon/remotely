package srpc

import scala.reflect.runtime.universe.TypeTag
import scodec.Encoder

case class Encoders(encoders: Map[String,Encoder[Any]]) {

  def encoder[A:TypeTag:Encoder]: Encoders = {
    val name = Remote.toTag(implicitly[TypeTag[A]])
    this.copy(encoders = encoders + (name -> Encoder[A].asInstanceOf[Encoder[Any]]))
  }

  def keySet = encoders.keySet

  def get(k: String): Option[Encoder[Any]] = encoders.get(k)
}

object Encoders {
  val empty = Encoders(Map())
}
