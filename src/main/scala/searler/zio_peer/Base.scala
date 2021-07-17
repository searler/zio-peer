package searler.zio_peer

import searler.zio_tcp.TCP.Channel
import zio._
import zio.stream.{ZStream, ZTransducer}

object Base {

  def apply[A, T, S, U](input: ZTransducer[Any, Nothing, Byte, S],
                        output: U => Chunk[Byte],
                        tracker: Monitor[A],
                        hub: ZHub[Any, Any, Nothing, Nothing, (AddressSpec[A], T), (AddressSpec[A], U)],
                        processor: Enqueue[(A, S)]): (A, Channel) => ZIO[Any, Nothing, Unit] = (addr: A, c: Channel) => {
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

    (for {
      promise <- Promise.make[Nothing, Unit]
      _ <- writer(addr, promise, c).fork
      _ <- reader(addr, promise, c)
    } yield ()).ensuring(tracker.destroyed(addr, c.close))
  }
}
