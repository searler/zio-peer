package searler.zio_peer

import zio._
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.stream._

import java.net.SocketAddress


object DriverNoString extends App {

  val EOL = Chunk.single[Byte]('\n')

  sealed trait DTO

  sealed trait Request extends DTO

  case class NameRequest(name: String) extends Request

  sealed trait Response extends DTO

  case class NameResponse(data: String) extends Response

  implicit val decoder: JsonDecoder[Request] = DeriveJsonDecoder.gen[Request]
  implicit val encoder: JsonEncoder[Response] = DeriveJsonEncoder.gen[Response]

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    def process(pair: (SocketAddress, Request)): (AddressSpec[SocketAddress], Response) = pair match {
      case (addr: SocketAddress, nr: NameRequest) => (ALL, NameResponse(nr.name))
      case _ => (ALL, NameResponse("----"))
    }

    val program = for {

      responseHub <-
        ZHub.sliding[(AddressSpec[SocketAddress], Response)](20)

      requestHub <- ZHub.sliding[(SocketAddress, Request)](20)

      _ <- ZStream.fromHub(requestHub).map(process).run(ZSink.fromHub(responseHub)).fork

      exclusivity <- Tracker.dropOld[SocketAddress]

      server <- Acceptor[SocketAddress, Response, Request, Response](8886, 200,
        Option.apply,
        (Transducer.utf8Decode >>> ZTransducer.splitLines).map(_.fromJson[Request]).filter(_.isRight).map(_.fold(_ => null, Predef.identity)),
        response => Chunk.fromArray(response.toJson.getBytes("UTF8")) ++ EOL,
        exclusivity,
        responseHub,
        requestHub.toQueue
      ).fork

      _ <- console.getStrLn
      _ <- server.interrupt
    }
    yield ()

    program.exitCode

  }
}
