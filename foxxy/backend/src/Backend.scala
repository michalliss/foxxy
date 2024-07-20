package foxxy.backend

import cats.syntax.all.*
import org.http4s.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.CORS
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.*
import zio.*
import zio.interop.catz.*

type ServerEndpoint = ZServerEndpoint[Any, Any]

case class Backend(endpoints: List[ServerEndpoint]) {
  val docs                        = SwaggerInterpreter().fromServerEndpoints[Task](endpoints, "app", "1.0.0")
  val allRoutes: HttpRoutes[Task] = ZHttp4sServerInterpreter().from(endpoints ++ docs).toRoutes

  val cors = CORS.policy.withAllowOriginAll.withAllowMethodsAll
    .withAllowCredentials(false)
    .apply(allRoutes)

  val serve = for {
    _ <- ZIO.executor.flatMap(executor =>
           BlazeServerBuilder[Task]
             .withExecutionContext(executor.asExecutionContext)
             .bindHttp(5004, "localhost")
             .withHttpApp(Router("/" -> (cors)).orNotFound)
             .serve
             .compile
             .drain
         )
  } yield ()
}
