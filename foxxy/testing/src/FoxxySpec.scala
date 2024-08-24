package foxxy.testing

import zio.*
import zio.logging.*
import zio.logging.slf4j.bridge.Slf4jBridge
import zio.test.*

abstract class FoxxySpec extends ZIOSpecDefault {
  override val bootstrap = Slf4jBridge.init() >>> testEnvironment
}
