package searler.zio_peer

import searler.zio_tcp.TCP.Channel
import zio.blocking.Blocking
import zio.stream.{ZStream, ZTransducer}
import zio.{Chunk, Enqueue, Schedule, ZHub, ZIO}

object Connector {

  def apply[A, T, S, U, C](acceptors: Set[A],
                           builder: A => ZIO[Blocking, Throwable, Channel],
                           input: ZTransducer[Any, Nothing, Byte, S],
                           output: U => Chunk[Byte],
                           tracker: ConnectorTracker[A],
                           hub: ZHub[Any, Any, Nothing, Nothing, (Routing[A], T), (Routing[A], U)],
                           processor: Enqueue[(A, S)],
                           reconnector: Schedule[Any, Any, C],
                           initial: Iterable[S] = Seq.empty)
  = {
    val base = BaseServer(input, output, tracker, hub, processor, initial)

    for {
      _ <- ZStream.fromIterable(acceptors).mapMParUnordered(acceptors.size) {
        addr => {
          (for {
            c <- builder(addr)
            _ <- tracker.created(addr, c)
            _ <- base(addr, c)
          } yield ()).repeat(reconnector).retry(reconnector).either
        }
      }.runDrain
    }
    yield ()
  }

}