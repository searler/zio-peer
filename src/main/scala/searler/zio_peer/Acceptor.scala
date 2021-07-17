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
                        exclusive: Tracker[A],
                        hub: ZHub[Any, Any, Nothing, Nothing, (AddressSpec[A], T), (AddressSpec[A], U)],
                        processor: Enqueue[(A, S)]): ZIO[Blocking, Throwable, Unit] = {
    val base = Base(input, output, exclusive, hub, processor)

    for {
      _ <- TCP.fromSocketServer(port)
        .mapMParUnordered(parallelism) {
          c =>
            (for {
              looked <- c.remoteAddress.map(lookup)
              addr <- exclusive.created(looked, c.close)
              _ <- base(addr.get, c).when(addr.isDefined)
            }
            yield ()).ensuring(c.close())
        }
        .runDrain
    } yield ()
  }
}
