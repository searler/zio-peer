package io.github.searler.zio_peer

import io.github.searler.zio_tcp.TCP.Channel
import zio.UIO
import zio.stream.SubscriptionRef

sealed trait AcceptorTracker[A] extends Tracker[A] {
  def created(addr: Option[A], channel: Channel): UIO[Option[A]]
}

object AcceptorTracker {
  type Finalizer = () => UIO[Unit]

  def policy[A](retainNew: A => Boolean): UIO[AcceptorTracker[A]] = SubscriptionRef.make(Map.empty[A, Channel]).map(new Policy(_, retainNew))

  def dropNew[A]: UIO[AcceptorTracker[A]] = policy(_ => false)

  def dropOld[A]: UIO[AcceptorTracker[A]] = policy(_ => true)

  private final class Policy[A](protected val state: SubscriptionRef[Map[A, Channel]], private val retainNew: A => Boolean) extends Tracker.Base[A] with AcceptorTracker[A] {

    def created(addr: Option[A], channel: Channel): UIO[Option[A]] = addr match {
      case None => channel.close() *> UIO(addr)
      case Some(a) => state.ref.modify { current =>
        current.get(a) match {
          case None => UIO(addr, current + (a -> channel))
          case Some(existing) =>
            if (retainNew(a))
              existing.close() *> UIO(addr -> (current + (a -> channel)))
            else
              channel.close() *> UIO((None, current))
        }
      }
    }
  }

}
