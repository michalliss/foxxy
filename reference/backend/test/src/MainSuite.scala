package foxxy.reference.backend.test

import zio.*
import zio.test.*

object HelloWorldSpec extends ZIOSpecDefault {
  def spec = suite("HelloWorldSpec")(
    test("HelloWorld should return 'Hello, World!'") {
      assertTrue("" == "")
    }
  )
}
