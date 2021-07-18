package searler.zio_peer

import searler.zio_tcp.TCP.Channel
import zio.UIO
import zio.stream.{SubscriptionRef, UStream}

private [zio_peer] trait Tracker[A] {


  def destroyed(addr: A,channel:Channel): UIO[Unit]

  def get: UIO[Set[A]]

  def changes: UStream[Set[A]]
}


private [zio_peer] object Tracker {

  abstract class Base[A]() extends Tracker[A] {

    protected val state: SubscriptionRef[Map[A, Channel]]

    def destroyed(addr: A, channel:Channel): UIO[Unit] = state.ref.update(current => current.get(addr) match {
      case Some(c) if channel == c => UIO(current - addr)
      case _ @ x => UIO(current)
    }) *> channel.close()

    def changes = state.changes.map(_.keys.toSet)

    def get: UIO[Set[A]] = state.ref.get.map(_.keys.toSet)

  }

}