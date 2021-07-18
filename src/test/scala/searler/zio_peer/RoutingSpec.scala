package searler.zio_peer

import zio.test.Assertion.{isFalse, isTrue}
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

object RoutingSpec extends DefaultRunnableSpec {
  override def spec = suite("addressing")(
    suite("ALL")(
      test("matches Unit") {
        assert(ALL.matches(()))(isTrue)
      },
        test("matches null") {
        assert(ALL.matches(null))(isTrue)
      }
      ,
      test("matches string") {
        assert(ALL.matches("XXX"))(isTrue)
      }
    ),

      suite("Single")(

      test("matches") {
        assert(Single("X").matches("X"))(isTrue)
      },
        test("no match") {
          assert(Single("X").matches("Y"))(isFalse)
        }
    ),
    suite("AllBut")(

      test("matches") {
        assert(AllBut("X").matches("Not X"))(isTrue)
      },
      test("no match") {
        assert(AllBut("X").matches("X"))(isFalse)
      }
    )
  )
}
