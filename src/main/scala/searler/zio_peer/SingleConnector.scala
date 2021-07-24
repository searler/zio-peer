package searler.zio_peer

import searler.zio_tcp.TCP.Channel
import zio.blocking.Blocking
import zio.stream.{Transducer, ZStream, ZTransducer}
import zio.{Chunk, Enqueue, Promise, Schedule, UIO, ZHub, ZIO}

object SingleConnector {

  private val EOL = Chunk.single[Byte]('\n')

  def apply[T, S, U, C](
                         builder: => ZIO[Blocking, Throwable, Channel],
                         decoder: ZTransducer[Any, Nothing, Byte, S],
                         encoder: U => Chunk[Byte],
                         tracker: Enqueue[Boolean],
                         source: ZHub[Any, Any, Nothing, Nothing, T, U],
                         processor: Enqueue[S],
                         reconnector: Schedule[Any, Any, C],
                         initial: Iterable[S] = Seq.empty)
  = {
    def base(c: Channel) = {
      def reader(promise: Promise[Nothing, Unit], c: Channel): UIO[Unit] =
        (for {
          _ <- promise.await
          result <- (ZStream.fromIterable(initial) ++ c.read.transduce(decoder))
            .foreach(line => processor.offer(line)).ensuring(c.close())
        }
        yield result).catchAll(_ => c.close())

      def writer(promise: Promise[Nothing, Unit], c: Channel): UIO[Unit] = {
        val managed = ZStream.fromHubManaged(source).tapM(_ => promise.succeed(()))
        val hubStream = ZStream.unwrapManaged(managed)

        val bytes = hubStream.mapConcatChunk(encoder)
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


  def strings[T, C](
                     builder: => ZIO[Blocking, Throwable, Channel],
                     tracker: Enqueue[Boolean],
                     source: ZHub[Any, Any, Nothing, Nothing, T, String],
                     processor: Enqueue[String],
                     reconnector: Schedule[Any, Any, C],
                     initial: Iterable[String] = Seq.empty) = apply[T, String, String, C](
    builder,
    decoder,
    encoder,
    tracker,
    source,
    processor,
    reconnector,
    initial)

}