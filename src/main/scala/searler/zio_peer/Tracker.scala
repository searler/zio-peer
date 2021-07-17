package searler.zio_peer

import zio.UIO
import zio.stream.SubscriptionRef

trait Tracker[A] extends Monitor[A] {
  def created(addr: Option[A], finalizer: Finalizer): UIO[Option[A]]
}

object Tracker {
  type Finalizer = () => UIO[Unit]

  def policy[A](retainNew: A => Boolean): UIO[Tracker[A]] = SubscriptionRef.make(Map.empty[A, Finalizer]).map(new Policy(_, retainNew))

  def dropNew[A]: UIO[Tracker[A]] = policy(_ => false)

  def dropOld[A]: UIO[Tracker[A]] = policy(_ => true)

  private final class Policy[A](protected val state: SubscriptionRef[Map[A, Finalizer]], private val retainNew: A => Boolean) extends Monitor.Base[A] with Tracker[A] {

    def created(addr: Option[A], finalizer: Finalizer): UIO[Option[A]] = addr match {
      case None => UIO(addr)
      case Some(a) => state.ref.modify { current =>
        current.get(a) match {
          case None => UIO(addr, current + (a -> finalizer))
          case Some(existing) =>
            if (retainNew(a))
              existing() *> UIO(addr -> (current + (a -> finalizer)))
            else
              finalizer() *> UIO((None, current))
        }
      }
    }
  }

}
