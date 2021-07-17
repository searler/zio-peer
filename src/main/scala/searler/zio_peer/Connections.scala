package searler.zio_peer

import zio.UIO
import zio.stream.SubscriptionRef

trait Connections[A] extends Monitor[A] {
  def created(addr: A, finalizer: Finalizer): UIO[Unit]
}

object Connections {
  type Finalizer = () => UIO[Unit]

  def apply[A]: UIO[Connections[A]] = SubscriptionRef.make(Map.empty[A, Finalizer]).map(new Recorder(_))

  private final class Recorder[A](protected val state: SubscriptionRef[Map[A, Finalizer]]) extends Monitor.Base[A] with Connections[A] {

    def created(addr: A, finalizer: Finalizer): UIO[Unit] =
      state.ref.update { current =>
        current.get(addr) match {
          case None => UIO(current + (addr -> finalizer))
          case Some(c) =>
            UIO(current + (addr -> finalizer))
        }
      }
  }
}