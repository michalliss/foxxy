package foxxy.backend

import org.http4s.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.CORS
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.*
import zio.interop.catz.*

case class Backend(config: BackendConfig) {
  def serve(routes: List[FoxxyServerEndpoint]) = {
    val docs                        = SwaggerInterpreter().fromServerEndpoints[Task](routes, "app", "1.0.0")
    val allRoutes: HttpRoutes[Task] = ZHttp4sServerInterpreter().from(routes ++ docs).toRoutes

    val cors = CORS.policy.withAllowOriginAll.withAllowMethodsAll
      .withAllowCredentials(false)
      .apply(allRoutes)

    for {
      _ <- ZIO.executor.flatMap(executor =>
             BlazeServerBuilder[Task]
               .withExecutionContext(executor.asExecutionContext)
               .bindHttp(config.port, "0.0.0.0")
               .withHttpApp(Router("/" -> (cors)).orNotFound)
               .serve
               .compile
               .drain
           )
    } yield ()
  }
}

object Backend {
  def live = ZLayer.derive[Backend]
}
