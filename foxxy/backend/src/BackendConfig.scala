package foxxy.backend

import cats.syntax.all._
import zio._

case class BackendConfig(port: Int)

object BackendConfig {
  def withPort(port: Int) = ZLayer.succeed(BackendConfig(port))
}