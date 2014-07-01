package remotely

import scala.reflect.runtime.universe.TypeTag
import scodec.Encoder

case class Encoders(encoders: Map[String,Encoder[Any]]) {

  def encoder[A:TypeTag:Encoder]: Encoders = {
    val name = Remote.toTag(implicitly[TypeTag[A]])
    this.copy(encoders = encoders + (name -> Encoder[A].asInstanceOf[Encoder[Any]]))
  }

  def ++(e: Encoders): Encoders = Encoders { encoders ++ e.encoders }

  def keySet = encoders.keySet

  def get(k: String): Option[Encoder[Any]] = encoders.get(k)

  def pretty =
    "Encoders.empty\n  " + encoders.keySet.toList.sorted.map(e => s".encoder[$e]").mkString("\n  ")

  override def toString = pretty
}

object Encoders {
  val empty = Encoders(Map())
}
