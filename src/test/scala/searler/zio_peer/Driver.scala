package searler.zio_peer

import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.stream.{Transducer, ZSink, ZStream, ZTransducer}
import zio.{App, Chunk, ExitCode, URIO, ZHub, console}

import java.net.{InetAddress, InetSocketAddress}

object Driver extends App {

  val EOL = Chunk.single[Byte]('\n')

  sealed trait DTO

  sealed trait Request extends DTO

  case class NameRequest(name: String) extends Request

  sealed trait Response extends DTO

  case class NameResponse(data: String) extends Response

  implicit val decoder: JsonDecoder[Request] = DeriveJsonDecoder.gen[Request]
  implicit val encoder: JsonEncoder[Response] = DeriveJsonEncoder.gen[Response]

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    def process(pair: (InetAddress, Request)): (Routing[InetAddress], Response) = pair match {
      case (addr: InetAddress, nr: NameRequest) => (ALL, NameResponse(nr.name))
      case _ => (ALL, NameResponse("----"))
    }

    val program = for {

      responseHub <-
        ZHub.sliding[(Routing[InetAddress], String)](20).map(_
          .contramap((resp: (Routing[InetAddress], Response)) => (resp._1, resp._2.toJson)))

      requestHub <- ZHub.sliding[(InetAddress, String)](20).map(_
        .map(req => (req._1, req._2.fromJson[Request]))
        .filterOutput(_._2.isRight)
        .map(pair => (pair._1, pair._2.fold(_ => null, Predef.identity))))

      _ <- ZStream.fromHub(requestHub).map(process).run(ZSink.fromHub(responseHub)).fork

      ex <- AcceptorTracker.dropOld[InetAddress]
      _ <- ex.changes.run(ZSink.foreach(keys => console.putStrLn(keys.toString()))).fork

      server <- Acceptor[InetAddress, Response, String, String](8886, 200,
        sa => Option(sa.asInstanceOf[InetSocketAddress].getAddress),
        Transducer.utf8Decode >>> ZTransducer.splitLines,
        str => Chunk.fromArray(str.getBytes("UTF8")) ++ EOL,
        ex,
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
