package foxxy.wsbackend

import zio.*
import zio.http.*
import zio.http.Header.AccessControlAllowOrigin
import zio.http.Middleware.{CorsConfig, cors}

case class WsBackend(config: WsBackendConfig) {

  def serve(app: WebSocketApp[Any]) = {
    val corsConfig = CorsConfig(allowedOrigin = _ => Some(AccessControlAllowOrigin.All))
    val http       = Routes(Method.GET / "ws" -> handler(app.toResponse)) @@ cors(corsConfig)

    for {
      _ <- Server.serve(http).provide(Server.defaultWithPort(config.port))
    } yield ()
  }
}

object WsBackend {
  def live = ZLayer.derive[WsBackend]
}
