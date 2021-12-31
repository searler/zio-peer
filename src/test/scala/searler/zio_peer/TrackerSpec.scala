package searler.zio_peer

import searler.zio_tcp.TCP.Channel
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, assert}

import java.net.{SocketAddress, SocketOption}
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousSocketChannel, CompletionHandler}
import java.util.concurrent.{Future, TimeUnit}
import java.{lang, util}

object TrackerSpec extends DefaultRunnableSpec {
  override def spec = suite("Tracker")(
    suite("AcceptorTracker")(
      suite("dropNew")(
        test("created None") {
          val socket = new MockSocket
          for {
            tracker <- AcceptorTracker.dropNew[String]
            result <- tracker.created(None, new Channel(socket))
            contents <- tracker.get
          }
          yield assert(result)(isNone) && assert(socket.isOpen)(isFalse) &&
            assert(contents)(isEmpty)
        },
        test("created Some") {
          val socket = new MockSocket
          for {
            tracker <- AcceptorTracker.dropNew[String]
            result <- tracker.created(Some("X"), new Channel(socket))
            contents <- tracker.get
          }
          yield assert(result)(equalTo(Some("X"))) && assert(socket.isOpen)(isTrue) &&
            assert(contents)(equalTo(Set("X")))
        }
        ,
        test("created duplicate") {
          val socket1 = new MockSocket
          val socket2 = new MockSocket
          for {
            tracker <- AcceptorTracker.dropNew[String]
            result1 <- tracker.created(Some("X"), new Channel(socket1))
            result2 <- tracker.created(Some("X"), new Channel(socket2))
            contents <- tracker.get
          }
          yield assert(result1)(equalTo(Some("X"))) && assert(socket1.isOpen)(isTrue) &&
            assert(result2)(isNone) && assert(socket2.isOpen)(isFalse) &&
            assert(contents)(equalTo(Set("X")))
        },

        test("created distinct") {
          val socket1 = new MockSocket
          val socket2 = new MockSocket
          for {
            tracker <- AcceptorTracker.dropNew[String]
            result1 <- tracker.created(Some("X"), new Channel(socket1))
            result2 <- tracker.created(Some("Y"), new Channel(socket2))
            contents <- tracker.get
          }
          yield assert(result1)(equalTo(Some("X"))) && assert(socket1.isOpen)(isTrue) &&
            assert(result2)(equalTo(Some("Y"))) && assert(socket2.isOpen)(isTrue) &&
            assert(contents)(equalTo(Set("X", "Y")))
        }

      ),


      suite("dropOld")(
        test("created None") {
          val socket = new MockSocket
          for {
            tracker <- AcceptorTracker.dropOld[String]
            result <- tracker.created(None, new Channel(socket))
            contents <- tracker.get
          }
          yield assert(result)(isNone) && assert(socket.isOpen)(isFalse) &&
            assert(contents)(isEmpty)
        },
        test("created Some") {
          val socket = new MockSocket
          for {
            tracker <- AcceptorTracker.dropOld[String]
            result <- tracker.created(Some("X"), new Channel(socket))
            contents <- tracker.get
          }
          yield assert(result)(equalTo(Some("X"))) && assert(socket.isOpen)(isTrue) &&
            assert(contents)(equalTo(Set("X")))
        }
        ,
        test("created duplicate") {
          val socket1 = new MockSocket
          val socket2 = new MockSocket
          for {
            tracker <- AcceptorTracker.dropOld[String]
            result1 <- tracker.created(Some("X"), new Channel(socket1))
            result2 <- tracker.created(Some("X"), new Channel(socket2))
            contents <- tracker.get
          }
          yield assert(result1)(equalTo(Some("X"))) && assert(socket1.isOpen)(isFalse) &&
            assert(result2)(equalTo(Some("X"))) && assert(socket2.isOpen)(isTrue) &&
            assert(contents)(equalTo(Set("X")))
        },

        test("created distinct") {
          val socket1 = new MockSocket
          val socket2 = new MockSocket
          for {
            tracker <- AcceptorTracker.dropOld[String]
            result1 <- tracker.created(Some("X"), new Channel(socket1))
            result2 <- tracker.created(Some("Y"), new Channel(socket2))
            contents <- tracker.get
          }
          yield assert(result1)(equalTo(Some("X"))) && assert(socket1.isOpen)(isTrue) &&
            assert(result2)(equalTo(Some("Y"))) && assert(socket2.isOpen)(isTrue) &&
            assert(contents)(equalTo(Set("X", "Y")))
        }
      )

    )
    , suite("ConnectorTracker")(


      test("created Some") {
        val socket = new MockSocket
        for {
          tracker <- ConnectorTracker[String]
          _ <- tracker.created("X", new Channel(socket))
          contents <- tracker.get
        }
        yield assert(socket.isOpen)(isTrue) &&
          assert(contents)(equalTo(Set("X")))
      }
      ,
      test("created duplicate") {
        val socket1 = new MockSocket
        val socket2 = new MockSocket
        for {
          tracker <- ConnectorTracker[String]
          _ <- tracker.created("X", new Channel(socket1))
          _ <- tracker.created("X", new Channel(socket2))
          contents <- tracker.get
        }
        yield assert(socket1.isOpen)(isFalse) &&
          assert(socket2.isOpen)(isTrue) &&
          assert(contents)(equalTo(Set("X")))
      },

      test("created distinct") {
        val socket1 = new MockSocket
        val socket2 = new MockSocket
        for {
          tracker <- ConnectorTracker[String]
          _ <- tracker.created("X", new Channel(socket1))
          _ <- tracker.created("Y", new Channel(socket2))
          contents <- tracker.get
        }
        yield assert(socket1.isOpen)(isTrue) &&
          assert(socket2.isOpen)(isTrue) &&
          assert(contents)(equalTo(Set("X", "Y")))
      }


    )


  )


  class MockSocket extends AsynchronousSocketChannel(null) {

    var open = true

    override def close(): Unit = open = false

    override def isOpen: Boolean = open

    override def bind(local: SocketAddress): AsynchronousSocketChannel = ???

    override def setOption[T](name: SocketOption[T], value: T): AsynchronousSocketChannel = ???

    override def shutdownInput(): AsynchronousSocketChannel = ???

    override def shutdownOutput(): AsynchronousSocketChannel = ???

    override def getRemoteAddress: SocketAddress = ???

    override def connect[A](remote: SocketAddress, attachment: A, handler: CompletionHandler[Void, _ >: A]): Unit = ???

    override def connect(remote: SocketAddress): Future[Void] = ???

    override def read[A](dst: ByteBuffer, timeout: Long, unit: TimeUnit, attachment: A, handler: CompletionHandler[Integer, _ >: A]): Unit = ???

    override def read(dst: ByteBuffer): Future[Integer] = ???

    override def read[A](dsts: Array[ByteBuffer], offset: Int, length: Int, timeout: Long, unit: TimeUnit, attachment: A, handler: CompletionHandler[lang.Long, _ >: A]): Unit = ???

    override def write[A](src: ByteBuffer, timeout: Long, unit: TimeUnit, attachment: A, handler: CompletionHandler[Integer, _ >: A]): Unit = ???

    override def write(src: ByteBuffer): Future[Integer] = ???

    override def write[A](srcs: Array[ByteBuffer], offset: Int, length: Int, timeout: Long, unit: TimeUnit, attachment: A, handler: CompletionHandler[lang.Long, _ >: A]): Unit = ???

    override def getLocalAddress: SocketAddress = ???

    override def getOption[T](name: SocketOption[T]): T = ???

    override def supportedOptions(): util.Set[SocketOption[_]] = ???


  }
}
