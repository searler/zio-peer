package searler.zio_peer

import searler.zio_tcp.TCP
import zio.stream.{ZStream, ZTransducer}
import zio.{Chunk, Enqueue, Schedule, ZHub}

object Connector {

  def apply[A, T, S, U, C](acceptors: Set[A],
                           lookup: A => (Int, String),
                           input: ZTransducer[Any, Nothing, Byte, S],
                           output: U => Chunk[Byte],
                           tracker: ConnectorTracker[A],
                           hub: ZHub[Any, Any, Nothing, Nothing, (Routing[A], T), (Routing[A], U)],
                           processor: Enqueue[(A, S)],
                           reconnector: Schedule[Any, Any, C],
                           initial: Iterable[S] = Seq.empty)
  = {
    val base = BaseServer(input, output, tracker, hub, processor)

    for {
      _ <- ZStream.fromIterable(acceptors).mapMParUnordered(acceptors.size) {
        addr => {
          val addrPair = lookup(addr)
          (for {
            c <- TCP.fromSocketClient(addrPair._1, addrPair._2)
            _ <- tracker.created(addr, c)
            _ <- base(addr, c)
          } yield ()).repeat(reconnector).retry(reconnector).either
        }
      }.runDrain
    }
    yield ()
  }

}