package searler.zio_peer

import searler.zio_tcp.TCP
import searler.zio_tcp.TCP.Channel
import zio._
import zio.blocking.Blocking
import zio.stream.{ZStream, ZTransducer}

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
    def reader(addr: A, promise: Promise[Nothing, Unit], c: Channel): UIO[Unit] =
      (for {
        _ <- promise.await
        result <- c.read.transduce(input)
          .foreach(line => processor.offer(addr, line)).ensuring(c.close())
      }
      yield result).catchAll(_ => c.close())

    def writer(addr: A, promise: Promise[Nothing, Unit], c: Channel): UIO[Unit] = {
      val managed = ZStream.fromHubManaged(hub).tapM(_ => promise.succeed(()))
      val hubStream = ZStream.unwrapManaged(managed)

      val items = hubStream.filter(_._1.matches(addr)).map(_._2)
      val bytes = items.mapConcatChunk(output)
      bytes.run(c.write).unit
    }.catchAll(_ => c.close())

    for {
      _ <- TCP.fromSocketServer(port)
        .mapMParUnordered(parallelism) {
          c =>
            (for {
              looked <- c.remoteAddress.map(lookup)
              addr <- exclusive.created(looked, c.close)
              _ <- (for {
                promise <- Promise.make[Nothing, Unit]
                _ <- writer(addr.get, promise, c).fork
                _ <- reader(addr.get, promise, c)
              } yield ()).ensuring(exclusive.destroyed(addr.get, c.close)).when(addr.isDefined)
            }
            yield ()).ensuring(c.close())
        }
        .runDrain
    } yield ()
  }
}
