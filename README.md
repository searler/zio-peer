# zio-peer
Simple peer-peer library using zio

## Summary

## Related projects

* TCP [library](https://github.com/searler/zio-tcp)

## Comparisons


## Documentation

### Examples

Echo in uppercase server

```scala

import io.github.searler.zio_peer.{ALL, Acceptor, AcceptorTracker, Routing}
import io.github.searler.zio_tcp.TCP
import zio.stream.{ZSink, ZStream}
import zio.{App, ExitCode, URIO, ZHub}

import java.net.{InetAddress, InetSocketAddress}

object StringServer extends App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val program = for {
      tracker <- AcceptorTracker.dropOld[InetAddress]

      responseHub <-
        ZHub.sliding[(Routing[InetAddress], String)](20)

      requestHub <- ZHub.sliding[(InetAddress, String)](20)

      _ <- ZStream.fromHub(requestHub).map(p => ALL -> (p._2.toUpperCase)).run(ZSink.fromHub(responseHub)).fork

      _ <- Acceptor.strings[InetAddress, String](TCP.fromSocketServer(8888),
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
```

Client sending line read from stdin

```scala

import io.github.searler.zio_peer.{ALL, Connector, ConnectorTracker, Routing}
import io.github.searler.zio_tcp.TCP
import zio.duration._
import zio.stream.{ZSink, ZStream}
import zio.{App, ExitCode, Schedule, URIO, ZHub, console}

object StringClient extends App {

  type Host = String

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val program = for {

      responseHub <-
        ZHub.sliding[(Routing[Host], String)](20)

      responseQueue = responseHub.toQueue

      requestHub <- ZHub.sliding[(Host, String)](20)

      _ <- ZStream.fromHub(requestHub).run(ZSink.foreach(result => console.putStrLn(result.toString))).forkDaemon

      ex <- ConnectorTracker[Host]
      _ <- ex.changes.run(ZSink.foreach(keys => console.putStrLn(keys.toString()))).forkDaemon

      _ <- (console.getStrLn.flatMap(line => responseQueue.offer(ALL -> line))).forever.forkDaemon

      connector <- Connector.strings[Host, String, Long](Set("localhost"),
        addr => TCP.fromSocketClient(8888, addr),
        ex,
        responseHub,
        requestHub.toQueue,
        reconnector = Schedule.spaced(1.second)
      )

    }
    yield ()

    program.exitCode

  }
}
```

See the test cases for further examples

## Releases
_still to be implemented_

## Planned enhancements

* TLS

## Background
