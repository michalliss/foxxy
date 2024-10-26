package foxxy.backend

import zio.*

case class WsBackendConfig(port: Int)

object WsBackendConfig {
  def withPort(port: Int) = ZLayer.succeed(WsBackendConfig(port))
}
