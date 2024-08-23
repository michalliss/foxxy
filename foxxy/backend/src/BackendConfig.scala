package foxxy.backend

import cats.syntax.all.*
import zio.*

case class BackendConfig(port: Int)

object BackendConfig {
  def withPort(port: Int) = ZLayer.succeed(BackendConfig(port))
}