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
/*

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.cert.Certificate
import scalaz.stream._
import scalaz.std.string._
import Process._

object SSLCert {
  val stripCruftFromPEM: Process1[String,String] = {
    val untilBegin: Process1[String,String] =
      receive1Or[String,String](fail(new IllegalArgumentException("Not a valid PAM, didn't find BEGIN marker"))){ s =>
        if(s startsWith "-----BEGIN")
          emit(s)
        else
          untilBegin
      }

    val untilEnd: Process1[String,String] = {
      receive1Or[String,String](fail(new IllegalArgumentException("Not a valid PEM, didn't find END marker")))  { s =>
        val i = s indexOf "-----END"
        if(i != 1)
          emit(s)
        else emit(s) ++ untilEnd
      }
    }

    (untilBegin ++ untilEnd)
  }

  def pemString(s: InputStream): Task[String] = (io.linesR(s) |> stripCruftFromPEM).foldMap(identity).runLast

  def certFromPEMStream(s: InputStream): Task[Certificate] = 
    pemStream(s) map (str => CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(str.getBytes("UTF8"))))

  def keyFromPkcs8(s: InputStream): Task[PrivateKey] = 
    pemStream(s) map (str => KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(str)))


  def sslContext(PrivateKey, Certificate): SSLContext = {
    val keystore.getInstance("JKS", "SUN")
    //Before a keystore can be accessed, it must be loaded.
    //Since we don't read keys from any file, we pass "null" and load certificate later in the code below
    keystoreTM.load(null);
    val keystoreTM = KeyStore.getInstance("JKS", "SUN")
    //Before a keystore can be accessed, it must be loaded.
    //Since we don't read keys from any file, we pass "null" and load certificate later in the code below
    keystoreTM.load(null);
  }
}

object SSLContextSupport {
/*
  val pemCertName = ServiceConfig[String]("intelmedia.ws.s2s.certificate.client.publiccert")
  val pemCertKey  = ServiceConfig[String]("intelmedia.ws.s2s.certificate.client.pvtkey")
  val caPemName   = ServiceConfig[String]("intelmedia.ws.s2s.certificate.ca")
 */

  private val clientPemCert = PEMHelper(SSLContextSupport.pemCertName)
  private val clientPemKey = PEMHelper(SSLContextSupport.pemCertKey)
  private val caPem = PEMHelper(SSLContextSupport.caPemName)

  implicit val s2sSSLContext: SSLContext = {
    //keystore is for client certificates and keys
    val keystore = KeyStore.getInstance("JKS", "SUN")

    //Before a keystore can be accessed, it must be loaded.
    //Since we don't read keys from any file, we pass "null" and load certificate later in the code below
    keystore.load(null);

    //keystoreTM is for CA certs
    val keystoreTM = KeyStore.getInstance("JKS", "SUN")

    //Before a keystore can be accessed, it must be loaded.
    //Since we don't read keys from any file, we pass "null" and load certificate later in the code below
    keystoreTM.load(null);

    //load the client certs
    for {
      clientCerts <- clientPemCert.publicCertificate
      certKeys <- clientPemKey.privateKey
    } {

      if(clientCerts.isEmpty)
        s2slog.warn("SSLContextSupport: No client certificates loaded. Consider setting the property intelmedia.ws.s2s.certificate.client.publiccert=cert_with_chain_internal.pem in your conf file.")

      if(certKeys.isEmpty)
        s2slog.warn("SSLContextSupport: No client private keys loaded. Consider setting the property intelmedia.ws.s2s.certificate.client.pvtkey=hsm_key.pem in your conf file")

      clientCerts foreach { clientCert => keystore.setCertificateEntry("clientCert", clientCert)}

      //Since we aren't serializing the keystore, it's OK to give any password here
      certKeys foreach { clientKey =>
        keystore.setKeyEntry("clientKey", clientKey, "changeit".toCharArray(), clientCerts.toArray[Certificate])
      }

    }

    for (caCerts <- caPem.publicCertificate) {
      caCerts foreach { caCert =>
        caCert match {
          case x509Cert: X509Certificate =>
            keystoreTM.setCertificateEntry(x509Cert.getSubjectDN.getName, caCert)
            s2slog.debug("Loading CA Cert:{}", x509Cert.getSubjectDN.getName)
          case _ =>  s2slog.warn("Non x509Certificate {} in CA cert file",caCert)
        }
      }
    }

    val kmf = KeyManagerFactory.getInstance("SunX509", "SunJSSE")
    kmf.init(keystore, "changeit".toCharArray())
    val km = kmf.getKeyManagers()

    val tmf = TrustManagerFactory.getInstance("SunX509", "SunJSSE")
    tmf.init(keystoreTM)

    val tm = tmf.getTrustManagers()
    val context = SSLContext.getInstance("TLS", "SunJSSE")
    context.init(km, tm, new SecureRandom())

    context
  }
}
 */
