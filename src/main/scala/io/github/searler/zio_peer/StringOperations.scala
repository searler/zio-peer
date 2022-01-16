package io.github.searler.zio_peer

import zio.Chunk
import zio.stream.{ZPipeline}

object StringOperations {

  private val EOL = Chunk.single[Byte]('\n')

  val decoder = ZPipeline.utf8Decode >>> ZPipeline.splitLines
  val encoder: String => Chunk[Byte] = str=> Chunk.fromArray(str.getBytes("UTF8")) ++ EOL

}
