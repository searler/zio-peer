package io.github.searler.zio_peer

import io.github.searler.zio_tcp.TCP.Channel
import zio.UIO
import zio.stream.SubscriptionRef

sealed trait ConnectorTracker[A] extends Tracker[A] {
  def created(addr: A, channel: Channel): UIO[Unit]
}

object ConnectorTracker {

  def apply[A]: UIO[ConnectorTracker[A]] = SubscriptionRef.make(Map.empty[A, Channel]).map(new Recorder(_))

  private final class Recorder[A](protected val state: SubscriptionRef[Map[A, Channel]]) extends Tracker.Base[A] with ConnectorTracker[A] {

    def created(addr: A, channel: Channel): UIO[Unit] =
      state.ref.update { current =>
        current.get(addr) match {
          case None => UIO(current + (addr -> channel))
          case Some(c) =>
            c.close() *> UIO(current + (addr -> channel))
        }
      }
  }
}