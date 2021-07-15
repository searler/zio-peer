package searler.zio_peer

import zio.UIO
import zio.stream.{SubscriptionRef, UStream}

trait Tracker[A] {
  type Finalizer = () => UIO[Unit]

  def created(addr: Option[A], finalizer: Finalizer): UIO[Option[A]]

  def destroyed(addr: A, finalizer: Finalizer): UIO[Unit]

  def get: UIO[Set[A]]

  def changes: UStream[Set[A]]
}

object Tracker {
  type Finalizer = () => UIO[Unit]

  def policy[A](retainNew: A => Boolean): UIO[Tracker[A]] = SubscriptionRef.make(Map.empty[A, Finalizer]).map(new Policy(_, retainNew))

  def dropNew[A]: UIO[Tracker[A]] = policy(_ => false)

  def dropOld[A]: UIO[Tracker[A]] = policy(_ => true)

  private final class Policy[A](private val state: SubscriptionRef[Map[A, Finalizer]], private val retainNew: A => Boolean) extends Tracker[A] {
    def destroyed(addr: A, finalizer: Finalizer): UIO[Unit] = state.ref.update(current => current.get(addr) match {
      case Some(c) if finalizer == c => UIO(current - addr)
      case _ => UIO(current)
    }) *> finalizer()

    def changes = state.changes.map(_.keys.toSet)

    def get: UIO[Set[A]] = state.ref.get.map(_.keys.toSet)

    def created(addr: Option[A], finalizer: Finalizer): UIO[Option[A]] = addr match {
      case None => UIO(addr)
      case Some(a) => state.ref.modify { current =>
        current.get(a) match {
          case None => UIO(addr, current + (a -> finalizer))
          case Some(c) =>
            if (retainNew(a))
              c() *> UIO(addr -> (current + (a -> finalizer)))
            else
              finalizer() *> UIO((None, current))
        }
      }
    }
  }

}
