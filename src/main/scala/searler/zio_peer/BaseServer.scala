package searler.zio_peer

import searler.zio_tcp.TCP.Channel
import zio._
import zio.Clock
import zio.stream.{ZPipeline, ZStream}

private[zio_peer] object BaseServer {
  val EOL:Byte = '\n'

  def apply[A, S, U](input: ZPipeline[Any, Nothing, Byte, S],
                        encoder: U => Chunk[Byte],
                        tracker: Tracker[A],
                        source: ZHub[Any, Any, Nothing, Nothing, _, (Routing[A], U)],
                        processor: Enqueue[(A, S)],
                        ignored:S=>Boolean,
                        initial: Iterable[S]): (A, Channel) => ZIO[Clock, Nothing, Unit] = (addr: A, c: Channel) => {
    def reader(addr: A, promise: Promise[Nothing, Unit], c: Channel): URIO[Clock,Unit] =
      (for {
        _ <- promise.await
        result <- (ZStream.fromIterable(initial) ++ c.read.via(input)).timeout(2.seconds).filterNot(ignored)
          .foreach(line => processor.offer(addr, line)).ensuring(c.close())
      }
      yield result).catchAll(_ => c.close())

    def writer(addr: A, promise: Promise[Nothing, Unit], c: Channel): URIO[Clock,Unit] = {
      val managed = ZStream.fromHubManaged(source).tapZIO((_: ZStream[Any, Nothing, (Routing[A], U)])  => promise.succeed(()))
      val hubStream = ZStream.unwrapManaged(managed)

      val items = hubStream.filter(_._1.matches(addr)).map(_._2)
      val bytes: ZStream[Clock, Nothing, Byte] = items.mapConcatChunk(encoder).mergeTerminateLeft((ZStream.unit ++ ZStream.tick(1.seconds)).as(EOL))
      bytes.run(c.write).unit
    }.catchAll(_ => c.close())

    (for {
      promise <- Promise.make[Nothing, Unit]
      _ <- writer(addr, promise, c).fork
      _ <- reader(addr, promise, c)
    } yield ()).ensuring(tracker.destroyed(addr, c))
  }
}
