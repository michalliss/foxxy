package foxxy.reference.shared

import foxxy.reference.shared.domain.PlaybackState
import zio.http.*
import zio.http.codec.HttpCodec
import zio.http.endpoint.*
import zio.schema.*

import java.util.UUID

object Endpoints {
  case class BadRequest(message: String) derives Schema
  case class Unauthorized(message: String) derives Schema
  case class LoginRequest(name: String, password: String) derives Schema

  def login = Endpoint(RoutePattern.POST / "login")
    .in[LoginRequest]
    .out[String]
    .outErrors[BadRequest | Unauthorized](
      HttpCodec.error[BadRequest](Status.BadRequest),
      HttpCodec.error[Unauthorized](Status.Unauthorized)
    )

  case class RegisterRequest(name: String, password: String) derives Schema
  def register = Endpoint(RoutePattern.POST / "register")
    .in[RegisterRequest]
    .outError[BadRequest](Status.BadRequest)
    .out[String]

  enum WsUserMessage derives Schema:
    case UpdateProgress(progress: PlaybackState)
    case RequestUpdate()
    case RequestLeader()

  enum WsServerMessage derives Schema:
    case UserId(userId: UUID)
    case Room(room: Room)
    case Msg(msg: String)
}
