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

package remotely.examples

import scala.concurrent.Future
import scalaz.concurrent.Task
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.net.ssl.TrustManager
import java.security.{SecureRandom, KeyStore}
import java.security.cert.{X509Certificate, Certificate}

object SSLSetup {

  def context(keyname: String, pemCertName: String, pemCertKey: String, caPemName: String,
              warn: String => Unit = println, debug: String => Unit = _ => ()): Task[SSLContext] = Task.delay {

    val clientPemCert = PEMHelper(pemCertName)
    val clientPemKey = PEMHelper(pemCertKey)
    val caPem = PEMHelper(caPemName)

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
    val clientCerts = clientPemCert.publicCertificate
    val certKeys = clientPemKey.privateKey
    clientCerts foreach { clientCert => keystore.setCertificateEntry("clientCert", clientCert)}

    //Since we aren't serializing the keystore, it's OK to give any password here
    certKeys foreach { clientKey =>
      keystore.setKeyEntry(keyname, clientKey, "changeit".toCharArray(), clientCerts.toArray[Certificate])
    }

    for (caCert <- caPem.publicCertificate) {
      caCert match {
        case x509Cert: X509Certificate =>
          keystoreTM.setCertificateEntry(x509Cert.getSubjectDN.getName, caCert)
          debug(s"Loading CA Cert: ${x509Cert.getSubjectDN.getName}")
        case _ => warn(s"Non x509Certificate $caCert in CA cert file")
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
