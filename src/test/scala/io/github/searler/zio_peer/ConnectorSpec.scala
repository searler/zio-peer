package io.github.searler.zio_peer

import io.github.searler.zio_tcp.TCP
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


        gatherResult <- ZStream.fromHub(inHub).filter(_._2 != "INITIAL").runHead.fork

        // wait for connection to server
        initComplete <- ZStream.fromHub(inHub).filter(_._2 == "INITIAL").runHead.fork

        server <- runServer().fork

        connector <- Connector[String, String, String, String, Long](Set("localhost"),
          addr => TCP.fromSocketClient(8887, addr, noDelay = true),
          Transducer.utf8Decode,
          str => Chunk.fromArray(str.getBytes("UTF8")),
          tracker,
          outHub,
          inHub.toQueue,
          Schedule.forever,
          _.isBlank,
          Seq("INITIAL")
        ).fork

        // wait for connection to server
        _ <- initComplete.join


        _ <- outHub.publish(ALL -> "command")

        result <- gatherResult.join

        _ <- connector.interrupt
        _ <- server.interrupt

      } yield assert(result)(equalTo(Some("localhost" -> "\u000bdpnnboe")))
    }

  )

  private final def runServer() =
    TCP
      .fromSocketServer(8887)
      .mapMParUnordered(4)(TCP.handlerServer(_ => _.map(b => (b + 1).asInstanceOf[Byte])))
      .runDrain

}
