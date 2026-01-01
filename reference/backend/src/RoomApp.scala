package cinematic.backend

import foxxy.backend.*
import foxxy.reference.backend.RoomRepository
import foxxy.reference.shared.Domain.Room
import foxxy.reference.shared.RoomEndpoints.{ServerMessage, UserMessage}
import foxxy.reference.shared.{Domain, RoomEndpoints}
import foxxy.shared.BadRequest
import sttp.tapir.ztapir.*
import zio.*
import zio.stm.*
import zio.stream.*

case class RoomApp(repository: RoomRepository, backend: Backend, wsBackend: WsBackend) {

  val roomsHandler: FoxxyServerEndpoint = RoomEndpoints.getRooms.zServerLogic(_ => ZSTM.atomically(repository.allRooms))

  val roomHandler: FoxxyServerEndpoint = RoomEndpoints.getRoom.zServerLogic { id => ZSTM.atomically(repository.roomById(id)) }

  val createRoom: FoxxyServerEndpoint = RoomEndpoints.createRoom.zServerLogic { request =>
    for {
      room <- Domain.Room.empty(request.name)
      _    <- ZSTM.atomically { repository.addRoom(room) }
    } yield room
  }

  val deleteRoom: FoxxyServerEndpoint = RoomEndpoints.deleteRoom.zServerLogic { id =>
    for {
      _ <- ZSTM.atomically { repository.removeRoom(id) }
    } yield "ok"
  }

  val joinRoom: FoxxyServerEndpoint = RoomEndpoints.joinRoom.zServerLogic { case (id, request) =>
    for {
      user <- Domain.RoomUser.create(request.username)
      room <- ZSTM.atomically {
                for {
                  room <- repository.roomById(id).someOrFail(BadRequest("Room not found"))
                  _    <- repository.updateRoom(room.addUser(user))
                } yield room
              }
    } yield room.id
  }

  val addVideo: FoxxyServerEndpoint = RoomEndpoints.addVideo.zServerLogic { case (id, request) =>
    for {
      video <- Domain.Video.create(request.url).orElseFail(BadRequest("Cannot parse video url"))
      res   <- ZSTM.atomically {
                 for {
                   room <- repository.roomById(id).someOrFail(BadRequest("Room not found"))
                   _    <- repository.updateRoom(room.enqueue(video))
                 } yield "ok"
               }
    } yield res
  }

  val playVideo: FoxxyServerEndpoint = RoomEndpoints.playVideo.zServerLogic { (roomId, videoId) =>
    ZSTM.atomically {
      for {
        room       <- repository.roomById(roomId).someOrFail(BadRequest("Room not found"))
        updatedRoom = room.playVideo(videoId).getOrElse(room)
        _          <- repository.updateRoom(updatedRoom)
      } yield "ok"
    }
  }

  val roomConnectionHandler: WsServerEndpoint = RoomEndpoints.roomConnectionStream.zServerLogic(roomId => {
    def updateRoom(fn: Room => Room) = ZSTM.atomically {
      for {
        room   <- repository.roomById(roomId).someOrFail(BadRequest("Room not found"))
        updated = fn(room)
        _      <- repository.updateRoom(updated)
      } yield updated
    }

    def fetchRoom = ZSTM.atomically(repository.roomById(roomId).someOrFail(BadRequest("Room not found")))

    def createUser = for {
      user <- Domain.RoomUser.create("User")
      room <- updateRoom(_.addUser(user))
    } yield user.id

    for {
      userId <- createUser
    } yield inputStream => {

      val mappedStream = inputStream.mapZIO(userMessage =>
        userMessage match
          case UserMessage.UpdateProgress(progress) => updateRoom(_.updateUserProgress(userId, progress)).map(ServerMessage.Room(_))
          case UserMessage.RequestUpdate()          => fetchRoom.map(ServerMessage.Room(_))
          case UserMessage.RequestLeader()          => updateRoom(_.setLeader(userId)).map(ServerMessage.Room(_))
      )

      (ZStream
        .from(ServerMessage.UserId(userId)))
        .merge(mappedStream)
        .merge(ZStream.fromZIO(ZIO.never.onInterrupt(updateRoom(_.removeUser(userId)).ignore)))
        .merge(ZStream.repeatZIOWithSchedule(fetchRoom.map(ServerMessage.Room(_)), Schedule.fixed(1.second)))
    }
  })

  val run = for {
    _    <- Random.setSeed(1L)
    room <- Domain.Room.empty("TestRoom")
    _    <- ZSTM.atomically(repository.addRoom(room))
    a    <- backend.serve(List(roomsHandler, roomHandler, createRoom, deleteRoom, joinRoom, addVideo, playVideo)).fork
    b    <- wsBackend.serve(roomConnectionHandler).fork
    _    <- Fiber.joinAll(List(a, b))
  } yield ()
}
