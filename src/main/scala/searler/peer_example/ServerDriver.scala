package searler.peer_example

import searler.zio_peer._
import searler.zio_tcp.TCP
import zio.stream.{ZSink, ZStream}
import zio.{Console, ZHub, ZIOAppDefault}

import java.net.{InetAddress, InetSocketAddress}

object ServerDriver extends ZIOAppDefault {

  override def run = {
    val program = for {
      tracker <- AcceptorTracker.dropOld[InetAddress]

      _ <- tracker.changes.run(ZSink.foreach(keys => Console.printLine(keys.toString()))).forkDaemon

      responseHub <-
        ZHub.sliding[(Routing[InetAddress], String)](20)

      requestHub <- ZHub.sliding[(InetAddress, String)](20)

      _ <- ZStream.fromHub(requestHub).map(p => ALL -> (p._2.toUpperCase)).run(ZSink.fromHub(responseHub)).fork

      _ <- Acceptor.strings[InetAddress](TCP.fromSocketServer(8888),
        20,
        sa => Option(sa.asInstanceOf[InetSocketAddress].getAddress),
        tracker,
        responseHub,
        requestHub.toQueue
      )

    } yield ()

    program.exitCode

  }
}
