package searler.peer_example

import searler.zio_peer.{ALL, Connector, ConnectorTracker, Routing}
import searler.zio_tcp.TCP
import zio.stream.{ZSink, ZStream}
import zio.{Console, Schedule, ZHub, ZIOAppDefault}
import zio._

object ClientDriver extends ZIOAppDefault{

  type Host = String

  def run = {

    val program = for {

      responseHub <-
        ZHub.sliding[(Routing[Host], String)](20)

      responseQueue = responseHub.toQueue

      requestHub <- ZHub.sliding[(Host, String)](20)

      _ <- ZStream.fromHub(requestHub).run(ZSink.foreach(result => Console.printLine(result.toString))).forkDaemon

      ex <- ConnectorTracker[Host]
      _ <- ex.changes.run(ZSink.foreach(keys => Console.printLine(keys.toString()))).forkDaemon

      _ <- (Console.readLine.flatMap(line => responseQueue.offer(ALL -> line))).forever.forkDaemon

      connector <- Connector.strings[Host, String, Long](Set("localhost"),
        addr => TCP.fromSocketClient(8888, addr),
        ex,
        responseHub,
        requestHub.toQueue,
        reconnector = Schedule.spaced(1.second)
      )
    }
      yield()

    program.exitCode
  }

}
