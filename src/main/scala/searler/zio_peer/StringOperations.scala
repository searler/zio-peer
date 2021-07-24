package searler.zio_peer

import zio.Chunk
import zio.stream.{Transducer, ZTransducer}

object StringOperations {

  private val EOL = Chunk.single[Byte]('\n')

  val decoder = Transducer.utf8Decode >>> ZTransducer.splitLines
  val encoder: String => Chunk[Byte] = str=> Chunk.fromArray(str.getBytes("UTF8")) ++ EOL

}
