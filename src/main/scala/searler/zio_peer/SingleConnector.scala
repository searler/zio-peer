package searler.zio_peer

import searler.zio_tcp.TCP.Channel

import zio.Clock
import zio.stream.{ ZPipeline, ZStream}
import zio.{Chunk, Enqueue, Promise, Schedule, UIO, URIO, ZHub, ZIO}
import zio._

object SingleConnector {

  private val EOL = Chunk.single[Byte]('\n')

  def apply[S, U, C](
                         builder: => ZIO[Any, Throwable, Channel],
                         decoder: ZPipeline[Any, Nothing, Byte, S],
                         encoder: U => Chunk[Byte],
                         tracker: Enqueue[Boolean],
                         source: ZHub[Any, Any, Nothing, Nothing, _, U],
                         processor: Enqueue[S],
                         reconnector: Schedule[Any, Any, C],
                         ignored:S=>Boolean,
                         initial: Iterable[S] = Seq.empty)
  = {
    def base(c: Channel) = {
      def reader(promise: Promise[Nothing, Unit], c: Channel): URIO[Clock,Unit] =
        (for {
          _ <- promise.await
          result <- (ZStream.fromIterable(initial) ++ c.read.via(decoder)).timeout(2.seconds).filterNot(ignored)
            .foreach(line => processor.offer(line)).ensuring(c.close())
        }
        yield result).catchAll(_ => c.close())

      def writer(promise: Promise[Nothing, Unit], c: Channel): URIO[Clock,Unit] = {
        val managed = ZStream.fromHubManaged(source).tapZIO((_: ZStream[Any, Nothing, U]) => promise.succeed(()))
        val hubStream = ZStream.unwrapManaged(managed)

        val bytes = hubStream.mapConcatChunk(encoder).mergeTerminateLeft(ZStream.tick(1.seconds).as('\n'.asInstanceOf[Byte]))
        bytes.run(c.write).unit
      }.catchAll(_ => c.close())

      (for {
        promise <- Promise.make[Nothing, Unit]
        _ <- writer(promise, c).fork
        _ <- reader(promise, c)
      } yield ()).ensuring(c.close() *> tracker.offer(false))
    }


    (for {
      c <- builder
      _ <- tracker.offer(true)
      _ <- base(c)
    } yield ()).repeat(reconnector).retry(reconnector).either

  }
  import StringOperations._


  def strings[C](
                     builder: => ZIO[Any, Throwable, Channel],
                     tracker: Enqueue[Boolean],
                     source: ZHub[Any, Any, Nothing, Nothing, _, String],
                     processor: Enqueue[String],
                     reconnector: Schedule[Any, Any, C],
                     initial: Iterable[String] = Seq.empty) = apply[String, String, C](
    builder,
    decoder,
    encoder,
    tracker,
    source,
    processor,
    reconnector,
    _.isBlank,
    initial)

}