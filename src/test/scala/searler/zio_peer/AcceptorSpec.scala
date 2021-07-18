package searler.zio_peer

import searler.zio_tcp.TCP
import zio.stream.{Transducer, ZSink, ZStream, ZTransducer}
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert}
import zio.{Chunk, Schedule, ZHub}

import java.net.{InetAddress, InetSocketAddress}

object AcceptorSpec extends DefaultRunnableSpec {
  override def spec = suite("acceptor")(

    testM("onet") {

      for {
        tracker <- AcceptorTracker.dropOld[InetAddress]

        responseHub <-
          ZHub.sliding[(Routing[InetAddress], String)](20)

        requestHub <- ZHub.sliding[(InetAddress, String)](20)

        _ <- ZStream.fromHub(requestHub).map(p => ALL -> (p._2.toUpperCase)).run(ZSink.fromHub(responseHub)).fork

        server <- Acceptor[InetAddress, String, String, String](8886, 20,
          sa => Option(sa.asInstanceOf[InetSocketAddress].getAddress),
          Transducer.utf8Decode,
          str => Chunk.fromArray(str.getBytes("UTF8")),
          tracker,
          responseHub,
          requestHub.toQueue
        ).fork

        result <- requestChunk()

        _ <- server.interrupt

      } yield assert(result)(equalTo("REQUEST"))
    }

  )

  private final def requestChunk() = for {
    conn <- TCP.fromSocketClient(8886, "localhost").retry(Schedule.forever)
    receive <- TCP.requestChunk(Chunk.fromArray("request".getBytes()))(conn)
  } yield new String(receive.toArray)
}
