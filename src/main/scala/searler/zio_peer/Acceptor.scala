package searler.zio_peer

import searler.zio_tcp.TCP.Channel
import zio._

import zio.Clock
import zio.stream.{ ZStream, ZPipeline}

import java.net.SocketAddress

object Acceptor {



  def apply[A, S, U](connections: ZStream[Any, Throwable, Channel],
                        parallelism: Int,
                        lookup: SocketAddress => Option[A],
                        decoder: ZPipeline[Any, Nothing, Byte, S],
                        encoder: U => Chunk[Byte],
                        tracker: AcceptorTracker[A],
                        source: ZHub[Any, Any, Nothing, Nothing, _, (Routing[A], U)],
                        processor: Enqueue[(A, S)],
                        ignored:S=>Boolean,
                        initial: Iterable[S] = Seq.empty): ZIO[Clock, Throwable, Unit] = {
    val base = BaseServer(decoder, encoder, tracker, source, processor, ignored,initial)

    for {
      _ <- connections
        .mapZIOParUnordered(parallelism) {
          c =>
            (for {
              looked <- c.remoteAddress.map(lookup)
              addr <- tracker.created(looked, c)
              _ <- ZIO.foreachDiscard(addr)(base(_, c))
            }
            yield ()).ensuring(c.close())
        }
        .runDrain
    } yield ()
  }

  import StringOperations._

  def strings[A](connections: ZStream[Any, Throwable, Channel],
                    parallelism: Int,
                    lookup: SocketAddress => Option[A],
                    tracker: AcceptorTracker[A],
                    source: Hub[ (Routing[A], String)],
                    processor: Enqueue[(A, String)],
                    initial: Iterable[String] = Seq.empty): ZIO[ Clock, Throwable, Unit] =
    apply[A,  String,String](connections,parallelism,lookup,
      decoder,
      encoder,
      tracker,source,processor,
      _.isBlank,
      initial)
}
