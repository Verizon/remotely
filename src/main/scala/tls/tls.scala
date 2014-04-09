package remotely

import javax.net.ssl._

/**
 * Convenience functions for building up a `javax.net.ssl.SSLContext` needed to
 * create the `javax.net.ssl.SSLEngine` used for SSL connections.
 */
package object tls {

  def default = () => SSLContext.getDefault.createSSLEngine

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

}
