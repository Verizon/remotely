package remotely

/**
 * An exception raised on the server which is returned to the client
 * to be reraised.
 */
case class ServerException(msg: String) extends Exception(msg)
