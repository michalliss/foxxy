package foxxy.backend

import cats.syntax.all.*
import org.http4s.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.websocket.WebSocketBuilder2
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir.*
import zio.*
import zio.interop.catz.*

type WsServerEndpoint = ZServerEndpoint[Any, ZioStreams & WebSockets]

case class WsBackend(ws: WsServerEndpoint) {
  val websocketRoutes: (WebSocketBuilder2[Task] => HttpRoutes[Task]) = ZHttp4sServerInterpreter()
    .fromWebSocket(ws)
    .toRoutes

  val serve = for {
    _ <- ZIO.executor.flatMap(executor =>
           BlazeServerBuilder[Task]
             .withExecutionContext(executor.asExecutionContext)
             .bindHttp(5005, "localhost")
             .withHttpWebSocketApp(wsb => Router("/" -> websocketRoutes(wsb)).orNotFound)
             .serve
             .compile
             .drain
         )
  } yield ()
}