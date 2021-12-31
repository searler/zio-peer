package searler.zio_peer

import searler.zio_tcp.TCP

import zio.Clock
import zio.stream.{ZPipeline, ZSink, ZStream}
import zio.test.Assertion.{equalTo, hasMessage, isLeft, isRight}
import zio.test.{DefaultRunnableSpec, assert}
import zio.{Chunk,  Schedule, URIO, ZHub}

import java.net.{InetAddress, InetSocketAddress, SocketAddress}

object AcceptorSpec extends DefaultRunnableSpec {


  def common(port:Int,lookup : SocketAddress => Option[InetAddress]): URIO[Clock, Either[Throwable, String]] =  (for {
    tracker <- AcceptorTracker.dropOld[InetAddress]

    responseHub <-
      ZHub.sliding[(Routing[InetAddress], String)](20)

    requestHub <- ZHub.sliding[(InetAddress, String)](20)

    _ <- ZStream.fromHub(requestHub).map(p => ALL -> (p._2.toUpperCase)).run(ZSink.fromHub(responseHub)).fork

    server <- Acceptor.strings[InetAddress](TCP.fromSocketServer(port, noDelay = true),
      20,
      lookup,
      tracker,
      responseHub,
      requestHub.toQueue
    ).fork

    result <- requestChunk(port)

   _ <- server.interrupt
  }yield result).either


    override def spec = suite("acceptor")(

    test("lookup accepts connection") {

      for {
       result <- common( 8886,sa => Option(sa.asInstanceOf[InetSocketAddress].getAddress))
      } yield assert(result)(isRight(equalTo("\n\nREQUEST\n")))
    },


      test("lookup rejects connection") {

      for {
        result <- common(8881, sa => Option.empty)
      } yield assert(result)(isLeft(hasMessage(equalTo("Connection reset by peer")))) ||
        assert(result)(isRight(equalTo("")))
    }

  )

  private final def requestChunk(port:Int) = for {
    conn <- TCP.fromSocketClient(port, "localhost", noDelay = true).retry(Schedule.forever)
    receive <- TCP.requestChunk(Chunk.fromArray("request\n".getBytes()))(conn)
  } yield new String(receive.toArray)
}
