package searler.zio_peer

import searler.zio_tcp.TCP
import searler.zio_tcp.TCP.Channel
import zio.stream.{ZStream, ZTransducer}
import zio.{Chunk, Enqueue, Promise, Schedule, UIO, ZHub, ZIO}

object Connector {

  def apply[A, T, S, U](acceptors: Set[A],
                        lookup: A => (Int, String),
                        input: ZTransducer[Any, Nothing, Byte, S],
                        output: U => Chunk[Byte],
                        connections: Connections[A],
                        hub: ZHub[Any, Any, Nothing, Nothing, (AddressSpec[A], T), (AddressSpec[A], U)],
                        processor: Enqueue[(A, S)],
                        initial:Iterable[S] = Seq.empty)
  = {
    val base = Base(input,output,connections,hub ,processor)

    for {
      _ <- ZStream.fromIterable(acceptors).mapMParUnordered(acceptors.size) {
        addr => {
          val addrPair = lookup(addr)
          (for {
            c <- TCP.fromSocketClient(addrPair._1, addrPair._2)
              _ <- connections.created(addr, c.close)
              _ <- base(addr,c)
          } yield ()).repeat(Schedule.forever).retry(Schedule.forever)
        }
      }.runDrain
    }
    yield ()
  }

}