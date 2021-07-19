package searler.zio_peer

import searler.zio_tcp.TCP
import zio.stream.{Transducer, ZStream}
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert}
import zio.{Chunk, Schedule, ZHub}

object ConnectorSpec extends DefaultRunnableSpec {
  override def spec = suite("connector")(

    testM("increment bytes") {

      for {
        tracker <- ConnectorTracker[String]
        tstream = tracker.changes.filter(!_.isEmpty)

        outHub <-
          ZHub.sliding[(Routing[String], String)](20)

        inHub <- ZHub.sliding[(String, String)](20)

        inStream = ZStream.fromHub(inHub)

        instreamFork <- inStream.runHead.fork

        server <- runServer().fork

        connector <- Connector[String, String, String, String, Long](Set("localhost"),
          addr => 8887 -> addr,
          Transducer.utf8Decode,
          str => Chunk.fromArray(str.getBytes("UTF8")),
          tracker,
          outHub,
          inHub.toQueue,
          Schedule.forever
        ).fork


        // wait for connection to server
        _ <- tstream.runHead

        _ <- outHub.publish(ALL -> "command")

        result <- instreamFork.join

        _ <- connector.interrupt
        _ <- server.interrupt

      } yield assert(result)(equalTo(Some("localhost" -> "dpnnboe")))
    }

  )

  private final def runServer() =
    TCP
      .fromSocketServer(8887)
      .mapMParUnordered(4)(TCP.handlerServer(_ => _.map(b => (b + 1).asInstanceOf[Byte])))
      .runDrain

}
