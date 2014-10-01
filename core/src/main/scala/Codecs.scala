package remotely

import scala.reflect.runtime.universe.TypeTag
import scodec.{Codec,Decoder,Encoder}

case class Codecs(codecs: Map[String,Codec[Any]]) {
  def codec[A:TypeTag:Codec]: Codecs = {
    val name = Remote.toTag(implicitly[TypeTag[A]])
    this.copy(codecs = codecs + (name -> Codec[A].asInstanceOf[Codec[Any]]))
  }
    

  def ++(c: Codecs): Codecs = Codecs(codecs ++ c.codecs)

  def keySet = codecs.keySet

  def get(k: String): Option[Codec[Any]] = codecs.get(k)

  def pretty =
    "Codecs.empty\n  " + codecs.keySet.toList.sorted.map(d => s".codec[$d]").mkString("\n  ")

  override def toString = pretty
}

object Codecs {

  val empty = Codecs(Map.empty)
}
