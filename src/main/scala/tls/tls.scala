package remotely

import javax.net.ssl._

/**
 * Convenience functions for building up a `javax.net.ssl.SSLContext` needed to
 * create the `javax.net.ssl.SSLEngine` used for SSL connections.
 */
package object tls {

  def default =
    () => SSLContext.getDefault.createSSLEngine
}
