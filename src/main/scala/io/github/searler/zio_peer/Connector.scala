package io.github.searler.zio_peer

import io.github.searler.zio_tcp.TCP.Channel
import zio.blocking.Blocking
import zio.stream.{Transducer, ZStream, ZTransducer}
import zio.{Chunk, Enqueue, Schedule, ZHub, ZIO}

object Connector {

  private val EOL = Chunk.single[Byte]('\n')

  def apply[A, T, S, U, C](acceptors: Set[A],
                           builder: A => ZIO[Blocking, Throwable, Channel],
                           decoder: ZTransducer[Any, Nothing, Byte, S],
                           encoder: U => Chunk[Byte],
                           tracker: ConnectorTracker[A],
                           source: ZHub[Any, Any, Nothing, Nothing, (Routing[A], T), (Routing[A], U)],
                           processor: Enqueue[(A, S)],
                           reconnector: Schedule[Any, Any, C],
                           ignored:S=>Boolean,
                           initial: Iterable[S] = Seq.empty)
  = {
    val base = BaseServer(decoder, encoder, tracker, source, processor, ignored, initial)

    for {
      _ <- ZStream.fromIterable(acceptors).mapMParUnordered(acceptors.size) {
        addr => {
          (for {
            c <- builder(addr)
            _ <- tracker.created(addr, c)
            _ <- base(addr, c)
          } yield ()).repeat(reconnector).retry(reconnector).either
        }
      }.runDrain
    }
    yield ()
  }

  import StringOperations._

  def strings[A, T, C](acceptors: Set[A],
                       builder: A => ZIO[Blocking, Throwable, Channel],
                       tracker: ConnectorTracker[A],
                       source: ZHub[Any, Any, Nothing, Nothing, (Routing[A], T), (Routing[A], String)],
                       processor: Enqueue[(A, String)],
                       reconnector: Schedule[Any, Any, C],
                       initial: Iterable[String] = Seq.empty) = apply[A, T, String, String, C](
    acceptors,builder,  decoder,encoder, tracker,source,processor,reconnector,
    _.isBlank,
    initial
  )

}