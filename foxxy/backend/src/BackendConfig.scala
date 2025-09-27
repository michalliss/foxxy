package foxxy.backend
import zio.*

case class BackendConfig(port: Int)

object BackendConfig {
  def withPort(port: Int) = ZLayer.succeed(BackendConfig(port))
}
