package searler.zio_peer

import zio.test.Assertion.isTrue
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
    ))
}
