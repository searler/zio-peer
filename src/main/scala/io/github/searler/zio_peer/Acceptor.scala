package io.github.searler.zio_peer

import io.github.searler.zio_tcp.TCP.Channel
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.stream.{Transducer, ZStream, ZTransducer}

import java.net.SocketAddress

object Acceptor {



  def apply[A, T, S, U](connections: ZStream[Blocking, Throwable, Channel],
                        parallelism: Int,
                        lookup: SocketAddress => Option[A],
                        decoder: ZTransducer[Any, Nothing, Byte, S],
                        encoder: U => Chunk[Byte],
                        tracker: AcceptorTracker[A],
                        source: ZHub[Any, Any, Nothing, Nothing, (Routing[A], T), (Routing[A], U)],
                        processor: Enqueue[(A, S)],
                        ignored:S=>Boolean,
                        initial: Iterable[S] = Seq.empty): ZIO[Blocking with Clock, Throwable, Unit] = {
    val base = BaseServer(decoder, encoder, tracker, source, processor, ignored,initial)

    for {
      _ <- connections
        .mapMParUnordered(parallelism) {
          c =>
            (for {
              looked <- c.remoteAddress.map(lookup)
              addr <- tracker.created(looked, c)
              _ <- ZIO.foreach_(addr)(base(_, c))
            }
            yield ()).ensuring(c.close())
        }
        .runDrain
    } yield ()
  }

  import StringOperations._

  def strings[A](connections: ZStream[Blocking, Throwable, Channel],
                    parallelism: Int,
                    lookup: SocketAddress => Option[A],
                    tracker: AcceptorTracker[A],
                    source: Hub[ (Routing[A], String)],
                    processor: Enqueue[(A, String)],
                    initial: Iterable[String] = Seq.empty): ZIO[Blocking with Clock, Throwable, Unit] =
    apply[A, String, String,String](connections,parallelism,lookup,
      decoder,
      encoder,
      tracker,source,processor,
      _.isBlank,
      initial)
}
