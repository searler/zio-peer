package searler.zio_peer

import zio.duration._
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps}
import zio.stream.{Transducer, ZSink, ZStream, ZTransducer}
import zio.{App, Chunk, ExitCode, Schedule, URIO, ZHub, console}

object ConnectionDriver extends App {

  type Host = String

  val EOL = Chunk.single[Byte]('\n')

  sealed trait DTO

  sealed trait Request extends DTO

  case class NameRequest(name: String) extends Request

  sealed trait Response extends DTO

  case class NameResponse(data: String) extends Response

  implicit val decoder = DeriveJsonEncoder.gen[Request]
  implicit val encoder = DeriveJsonDecoder.gen[Response]

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {


    val program = for {

      responseHub <-
        ZHub.sliding[(Routing[Host], String)](20).map(_
          .contramap((resp: (Routing[Host], Request)) => (resp._1, resp._2.toJson)))

      responseQueue = responseHub.toQueue

      requestHub <- ZHub.sliding[(Host, String)](20).map(_
        .map(req => (req._1, req._2.fromJson[Response]))
        .filterOutput(_._2.isRight)
        .map(pair => (pair._1, pair._2.fold(_ => null, Predef.identity))))

      _ <- ZStream.fromHub(requestHub).run(ZSink.foreach(result => console.putStrLn(result.toString))).forkDaemon

      ex <- ConnectorTracker[Host]
      _ <- ex.changes.run(ZSink.foreach(keys => console.putStrLn(keys.toString()))).forkDaemon

      _ <- (console.getStrLn.flatMap(line => responseQueue.offer(ALL -> NameRequest(line)))).repeatN(100).forkDaemon

      connector <- Connector[Host, Request, String, String, (Long, Long)](Set("localhost", "golem"),
        addr => 8886 -> addr,
        Transducer.utf8Decode >>> ZTransducer.splitLines,
        str => Chunk.fromArray(str.getBytes("UTF8")) ++ EOL,
        ex,
        responseHub,
        requestHub.toQueue,
        reconnector = Schedule.recurs(4) && Schedule.spaced(1.second)
      )

    }
    yield ()

    program.exitCode

  }
}
