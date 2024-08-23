package foxxy.backend

import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.websocket.WebSocketBuilder2
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir._
import zio._
import zio.interop.catz._

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