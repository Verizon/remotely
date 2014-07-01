package remotely

import akka.actor._

/** Utility functions for working with Akka actors. */
private[remotely] object Akka {

  /** Run the given action when `a` is terminated. */
  def onComplete(system: ActorSystem, a: ActorRef)(action: => Unit): Unit = {
    system.actorOf(Props(new Actor {
      context.watch(a)
      def receive = {
        case Terminated(`a`) => action; context stop self
      }
    }))
    ()
  }
}
