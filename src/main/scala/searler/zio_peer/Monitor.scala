package searler.zio_peer

import zio.UIO
import zio.stream.{SubscriptionRef, UStream}

trait Monitor[A] {
  type Finalizer = () => UIO[Unit]

  def destroyed(addr: A, finalizer: Finalizer): UIO[Unit]

  def get: UIO[Set[A]]

  def changes: UStream[Set[A]]
}


object Monitor {

  abstract class Base[A]() extends Monitor[A] {

    protected val state: SubscriptionRef[Map[A, Finalizer]]

    def destroyed(addr: A, finalizer: Finalizer): UIO[Unit] = state.ref.update(current => current.get(addr) match {
      case Some(c) if finalizer == c => UIO(current - addr)
      case _ => UIO(current)
    }) *> finalizer()

    def changes = state.changes.map(_.keys.toSet)

    def get: UIO[Set[A]] = state.ref.get.map(_.keys.toSet)

  }

}