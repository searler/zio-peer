package io.github.searler.zio_peer

import io.github.searler.zio_tcp.TCP.Channel
import zio.UIO
import zio.stream.{SubscriptionRef, UStream}

trait Tracked[A] {

  def get: UIO[Set[A]]

  def changes: UStream[Set[A]]
}


private[zio_peer] trait Tracker[A]  extends Tracked[A]{
  def destroyed(addr: A, channel: Channel): UIO[Unit]
}


private[zio_peer] object Tracker {

  abstract class Base[A]() extends Tracker[A] {

    protected val state: SubscriptionRef[Map[A, Channel]]

    def destroyed(addr: A, channel: Channel): UIO[Unit] = state.ref.update(current => current.get(addr) match {
      case Some(c) if channel == c => (current - addr)
      case _@x => (current)
    }) *> channel.close()

    def changes = state.changes.map(_.keys.toSet)

    def get: UIO[Set[A]] = state.ref.get.map(_.keys.toSet)

  }

}