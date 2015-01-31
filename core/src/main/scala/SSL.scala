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

import java.io.{ByteArrayInputStream,InputStream,File, FileInputStream}
import java.security.{KeyFactory,KeyStore,PrivateKey, SecureRandom}
import java.security.spec.{PKCS8EncodedKeySpec}
import java.security.cert.{Certificate,CertificateFactory,PKIXBuilderParameters,X509Certificate,X509CertSelector,TrustAnchor}
import javax.net.ssl._
import io.netty.handler.ssl.{ApplicationProtocolConfig,IdentityCipherSuiteFilter,SslContext,SslProvider}
import scalaz.stream._
import scalaz.std.string._
import scalaz.concurrent.Task
import scalaz.{Kleisli,Monoid}
import scodec.bits.ByteVector
import scalaz.syntax.traverse._
import scalaz.std.option._
import Process._
import collection.JavaConverters._

// what are all the configurations we want to support:
//
//  1. everything plaintext
//  2. traditional ssl
//  3. ssl + client cert

object SSL {

  def emptyKeystore: KeyStore = {
    val keystore = KeyStore.getInstance("JKS", "SUN")
    //Before a keystore can be accessed, it must be loaded.
    //Since we don't read keys from any file, we pass "null" and load certificate later in the code below
    keystore.load(null)
    keystore
  }

  def addPEM(in: InputStream, name: String)(ks: KeyStore): Task[KeyStore] = addCerts(ks, name, certFromPEM(in))

  implicit val taskUnitMonoid: Monoid[Task[Unit]] = new Monoid[Task[Unit]] {
    def zero = Task.now(())
    def append(a: Task[Unit], b: => Task[Unit]) = a flatMap { _ => b }
  }

  private[remotely] def addCert(cert: Certificate, name: String, ks: KeyStore): Task[Unit] = Task.delay(ks.setCertificateEntry(name, cert))
  private[remotely] def addKey(key: PrivateKey, certs: Seq[Certificate], name: String, pass: Array[Char], ks: KeyStore):Task[Unit] = Task.delay(ks.setKeyEntry(name, key, pass, certs.toArray[Certificate]))

  private[remotely] def addCerts(ks: KeyStore, name: String, certs: Process[Task,Certificate]): Task[KeyStore] = certs.zipWithIndex.runFoldMap{ case (c,i) => addCert(c,name+i,ks) }.map(_ => ks)

  private[remotely] def stripCruftFromPEM(withHeaders: Boolean): Process1[String,String] = {
    def untilBegin: Process1[String,String] =
      receive1Or[String,String](halt){ s =>
        if(s startsWith "-----BEGIN") {
          if(withHeaders) emit(s+"\n") else halt
        } else {
          untilBegin
        }
      }

    def untilEnd: Process1[String,String] = {
      receive1Or[String,String](fail(new IllegalArgumentException("Not a valid KEY, didn't find END marker")))  { s =>
        if(s startsWith "-----END")
          if(withHeaders) emit(s+"\n") else halt
        else if(withHeaders) (emit(s + "\n") ++ untilEnd) else (emit(s) ++ untilEnd)
      }
    }

    (untilBegin ++ untilEnd).foldMap(identity).repeat.filter(_.length > 0)

  }

  def pemString(s: InputStream, withHeaders: Boolean): Process[Task,String] = (io.linesR(s) |> stripCruftFromPEM(withHeaders))

  def certFromPEM(s: InputStream): Process[Task,Certificate] =
    pemString(s, true) map (str => CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(str.getBytes("US-ASCII"))))

  def keyFromPkcs8(s: InputStream): Process[Task,PrivateKey] =
    pemString(s, false).flatMap { str =>
      ByteVector.fromBase64(str).fold[Process[Task,PrivateKey]](Process.fail(new IllegalArgumentException("could not parse PEM data"))){ decoded =>
        val spec = new PKCS8EncodedKeySpec(decoded.toArray)
        emit(KeyFactory.getInstance("RSA").generatePrivate(spec))
      }
    }


  private[remotely] def keyFromEncryptedPkcs8(s: InputStream, pass: Array[Char]): Process[Task,PrivateKey] =
    pemString(s, false) map (str => KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(str.getBytes("US-ASCII"))))
}

case class SslParameters(caBundle: Option[File],
                         certFile: Option[File],
                         keyFile: Option[File],
                         keyPassword: Option[String],
                         enabledCiphers: Option[List[String]],
                         enabledProtocols: Option[List[String]],
                         requireClientAuth: Boolean) {
}

object SslParameters {

  private[remotely] def trustManagerForBundle(caBundle: File): Task[TrustManagerFactory] = {
    Task.delay {
      val keystore = SSL.emptyKeystore
      val trustSet = new java.util.HashSet[TrustAnchor]()

      // I promise that effects are associative as long as you execute them in the right order
      implicit val taskMonoid: Monoid[Task[Unit]] = Monoid.instance((a,b) => a flatMap(_ => b), Task.now(()))

      val doRun = SSL.certFromPEM(new FileInputStream(caBundle)).foldMap {
        case cert: X509Certificate =>
          val name = cert.getSubjectDN().getName()
          if(cert.getBasicConstraints != -1) trustSet.add(new TrustAnchor(cert, null))
          SSL.addCert(cert, name, keystore)
        case cert => throw new IllegalArgumentException("unexpected cert which is not x509")
      }
      doRun.run.run // da do run run: https://www.youtube.com/watch?v=uTqnam1zgiw

      val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
      val builder = new PKIXBuilderParameters(trustSet, new X509CertSelector)
      builder.setRevocationEnabled(false)
      val cptmp: CertPathTrustManagerParameters=new CertPathTrustManagerParameters(builder);

      tm.init(cptmp)
      tm
    }
  }

  private[remotely] def toClientContext(params: Option[SslParameters]): Task[Option[SslContext]] = {
    params.traverse { params =>
      for {
        tm <- params.caBundle.fold[Task[TrustManagerFactory]](Task.now(null))(trustManagerForBundle)

      } yield SslContext.newClientContext(SslProvider.JDK,
                                          null,
                                          tm,
                                          params.certFile.orNull,
                                          params.keyFile.orNull,
                                          params.keyPassword.orNull,
                                          null,
                                          params.enabledCiphers.map(_.asJava).orNull,
                                          IdentityCipherSuiteFilter.INSTANCE,
                                          ApplicationProtocolConfig.DISABLED,
                                          0,
                                          0)

    }
  }

  private[remotely] def toServerContext(params: Option[SslParameters]): Task[Option[SslContext]] = {
    params.traverse { params =>
      for {
        tm <- params.caBundle.fold[Task[TrustManagerFactory]](Task.now(null))(trustManagerForBundle)

      } yield SslContext.newServerContext(SslProvider.JDK,
                                          null,
                                          tm,
                                          params.certFile.orNull,
                                          params.keyFile.orNull,
                                          params.keyPassword.orNull,
                                          null,
                                          params.enabledCiphers.map(_.asJava).orNull,
                                          IdentityCipherSuiteFilter.INSTANCE,
                                          ApplicationProtocolConfig.DISABLED,
                                          0,
                                          0)
    }
  }
}

