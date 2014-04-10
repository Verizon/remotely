package remotely

import javax.net.ssl._

/**
 * Convenience functions for building up a `javax.net.ssl.SSLContext` needed to
 * create the `javax.net.ssl.SSLEngine` used for SSL connections.
 */
package object tls {

  def default = () => SSLContext.getDefault.createSSLEngine

  /** Create an `SSLEngine` provider from an `SSLContext`. */
  def fromContext(ctx: SSLContext) = () => {
    ctx.createSSLEngine
  }

  /** Modify the given provider to set client mode on the `SSLEngine`. */
  def client(ssl: () => SSLEngine): () => SSLEngine = () => {
    val engine = ssl()
    engine.setUseClientMode(true)
    engine
  }

  /**
   * Modify the given provider to set server mode on the `SSLEngine`,
   * and optionally require authentication of the client.
   */
  def server(ssl: () => SSLEngine, authenticateClient: Boolean = false): () => SSLEngine = () => {
    val engine = ssl()
    engine.setUseClientMode(false)
    if (authenticateClient) engine.setNeedClientAuth(true)
    // Not positive this will be the same as `engine.setNeedClientAuth(authenticateClient)`
    engine
  }

  /** Modify the given provider to enable the given cipher suites. */
  def enableCiphers(ciphers: Cipher*)(ssl: () => SSLEngine) =
    () => {
      val allowed = ciphers.map(_.name).toSet
      val engine = ssl()
      val supported = engine.getSupportedCipherSuites
      val enabled = supported.filter(allowed.contains)
      engine.setEnabledCipherSuites(enabled)
      engine
    }
}

package tls {
  case class Cipher(name: String)

  object ciphers {
    val TLS_DHE_RSA_WITH_AES_128_CBC_SHA  = Cipher("TLS_DHE_RSA_WITH_AES_128_CBC_SHA")
    val SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA = Cipher("SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA")
    val TLS_RSA_WITH_AES_128_CBC_SHA      = Cipher("TLS_RSA_WITH_AES_128_CBC_SHA")
    val SSL_RSA_WITH_3DES_EDE_CBC_SHA     = Cipher("SSL_RSA_WITH_3DES_EDE_CBC_SHA")

    // http://stackoverflow.com/questions/2238135/good-list-of-weak-cipher-suites-for-java
    val rsa = List(
      TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
      SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA,
      TLS_RSA_WITH_AES_128_CBC_SHA,
      SSL_RSA_WITH_3DES_EDE_CBC_SHA
    )
  }
}
