package foxxy.backend

import zio.*
import zio.http.*
import zio.http.Header.AccessControlAllowOrigin
import zio.http.Middleware.{CorsConfig, cors}
import zio.http.endpoint.*
import zio.http.endpoint.openapi.{OpenAPIGen, SwaggerUI}

case class Backend(config: BackendConfig) {
  def serve(
      endpoints: List[Endpoint[?, ?, ?, ?, ?]],
      routes: Chunk[Route[Any, Response]]
  ) = {
    val corsConfig    = CorsConfig(allowedOrigin = _ => Some(AccessControlAllowOrigin.All))
    val openAPI       = OpenAPIGen.fromEndpoints(title = "API", version = "1.0", endpoints)
    val swaggerRoutes = SwaggerUI.routes("docs", openAPI)

    val allRoutes = (Routes(routes) ++ swaggerRoutes) @@ cors(corsConfig)

    for {
      _ <- Server.serve(allRoutes).provide(Server.defaultWithPort(config.port))
    } yield ()
  }
}

object Backend {
  def live = ZLayer.derive[Backend]
}
