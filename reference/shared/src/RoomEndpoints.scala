package foxxy.reference.shared

import foxxy.reference.shared.Domain.PlaybackState
import foxxy.shared.*
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import zio.*
import zio.json.JsonCodec

import java.util.UUID

object RoomEndpoints {
  case class CreateRoomRequest(name: String) derives JsonCodec
  case class JoinRoomRequest(username: String) derives JsonCodec
  case class UpdateProgressRequest(position: Int) derives JsonCodec
  case class AddVideoRequest(url: String) derives JsonCodec

  val getRooms   = BaseEndpoints.publicEndpoint.get.in("room").out(jsonBody[List[Domain.Room]])
  val getRoom    = BaseEndpoints.publicEndpoint.get.in("room" / path[UUID]).out(jsonBody[Option[Domain.Room]])
  val createRoom = BaseEndpoints.publicEndpoint.post.in("room").in(jsonBody[CreateRoomRequest]).out(jsonBody[Domain.Room])
  val deleteRoom = BaseEndpoints.publicEndpoint.delete.in("room" / path[UUID]).out(jsonBody[String])
  val joinRoom   = BaseEndpoints.publicEndpoint.post.in("room" / path[UUID] / "join").in(jsonBody[JoinRoomRequest]).out(jsonBody[UUID])
  val playVideo  = BaseEndpoints.publicEndpoint.post.in("room" / path[UUID] / "video" / path[UUID] / "play").out(jsonBody[String])

  val createUser = BaseEndpoints.publicEndpoint.post.in("user").in(jsonBody[String]).out(jsonBody[Domain.RoomUser])
  val addVideo   = BaseEndpoints.publicEndpoint.post.in("room" / path[UUID] / "video").in(jsonBody[AddVideoRequest]).out(jsonBody[String])

  enum UserMessage derives JsonCodec:
    case UpdateProgress(progress: PlaybackState)
    case RequestUpdate()
    case RequestLeader()

  enum ServerMessage derives JsonCodec:
    case UserId(userId: UUID)
    case Room(room: Domain.Room)
    case Msg(msg: String)

  val roomConnectionStream = BaseEndpoints.publicEndpoint
    .in(path[UUID])
    .out(webSocketBody[UserMessage, CodecFormat.Json, ServerMessage, CodecFormat.Json](ZioStreams))
}
