package searler.zio_peer

import searler.zio_tcp.TCP
import zio._
import zio.blocking.Blocking
import zio.stream.ZTransducer

import java.net.SocketAddress

object Acceptor {

  def apply[A, T, S, U](port: Int,
                        parallelism: Int,
                        lookup: SocketAddress => Option[A],
                        input: ZTransducer[Any, Nothing, Byte, S],
                        output: U => Chunk[Byte],
                        tracker: AcceptorTracker[A],
                        hub: ZHub[Any, Any, Nothing, Nothing, (Routing[A], T), (Routing[A], U)],
                        processor: Enqueue[(A, S)]): ZIO[Blocking, Throwable, Unit] = {
    val base = BaseServer(input, output, tracker, hub, processor)

    for {
      _ <- TCP.fromSocketServer(port, noDelay = true)
        .mapMParUnordered(parallelism) {
          c =>
            (for {
              looked <- c.remoteAddress.map(lookup)
              addr <- tracker.created(looked, c)
              _ <- base(addr.get, c).when(addr.isDefined)
            }
            yield ()).ensuring(c.close())
        }
        .runDrain
    } yield ()
  }
}
