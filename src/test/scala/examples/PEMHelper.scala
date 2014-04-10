package remotely.examples

import javax.xml.bind.DatatypeConverter
import java.security.{KeyFactory, PrivateKey}
import java.security.spec.PKCS8EncodedKeySpec
import java.security.cert.{ Certificate, CertificateFactory }
import java.io.{ FileInputStream, InputStream, ByteArrayInputStream }
import io.Source
import scala.annotation.tailrec
import java.util.NoSuchElementException

case class PemIterator(pemPath: String, tag: String) extends Iterator[Array[Byte]] {

  var it: BufferedIterator[String] = Source.fromBytes(resourceBytes(pemPath)).getLines().buffered

  type CanClose = { def close(): Unit }
  def using[A <: CanClose, B](r: => A)(f: A => B): B = try f(r) finally r.close()

  //open resource stream
  //First look for resource in the jar file, then on the filesystem
  private def resourceStream(path: String): InputStream = {
    val stream = getClass.getResourceAsStream("/" + path)
    if (stream != null) stream
    else new FileInputStream(path)
  }

  private def readBytes(fis: InputStream): Array[Byte] =
    Stream.continually(fis.read).takeWhile(-1 !=).map(_.toByte).toArray

  private def resourceBytes(path: String): Array[Byte] = {
    using(resourceStream(path))(readBytes)
  }

  // The below function drops the data until the passed predicate is true, while keeping the iterator it in a well known state
  // we have to provide this special function instead of the available function dropWhile because
  // the documentation for it.dropWhile() states that once it's called, the original iterator state is undefined
  private def safeDropWhile(p: String => Boolean) : BufferedIterator[String]= {
    while (it.hasNext && p(it.head)) it.next()
    it
  }

  def next(): Array[Byte] = {
    if (hasNext)
      DatatypeConverter.parseBase64Binary(
        safeDropWhile(!_.contains("-----BEGIN " + tag)).drop(1).takeWhile(!_.contains("-----END " + tag)).mkString
      )
    else
      throw new NoSuchElementException

  }

  def hasNext: Boolean = safeDropWhile(!_.contains("-----BEGIN " + tag)).hasNext
}

object PEMHelper {
  def apply(pemPath: String) = new PEMHelper(pemPath)

  private val x509Factory: CertificateFactory = CertificateFactory.getInstance("X.509")
  private val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
}

/**
 * Utility methods to load keys from PEM file
 */
class PEMHelper(pemPath: String) {

  def publicCertificate: List[Certificate] =
    PemIterator(pemPath, "CERTIFICATE")
      .map{s =>
      PEMHelper.x509Factory.generateCertificate(new ByteArrayInputStream(s))}
      .toList

  def privateKey : List[PrivateKey] =
    PemIterator(pemPath, "PRIVATE KEY")
      .map(s => PEMHelper.keyFactory.generatePrivate(new PKCS8EncodedKeySpec(s)))
      .toList
}
