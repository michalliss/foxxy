package foxxy.reference.frontend.services

import foxxy.frontend.utils.makeWs
import foxxy.reference.shared.Endpoints

import java.util.UUID

case class MyWsClient() {
  val url              = "ws://localhost:5005"
  def ws(roomId: UUID) = makeWs[Endpoints.WsUserMessage, Endpoints.WsUserMessage](s"$url/${roomId}")
}
