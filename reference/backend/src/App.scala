package foxxy.reference.backend

import foxxy.auth.*
import foxxy.backend.*
import foxxy.reference.shared.Domain.Room
import foxxy.reference.shared.Endpoints.TodoResponse
import foxxy.reference.shared.RoomEndpoints.{ServerMessage, UserMessage}
import foxxy.reference.shared.domain.*
import foxxy.reference.shared.{Domain, Endpoints, RoomEndpoints}
import foxxy.repo.*
import foxxy.shared.*
import sttp.tapir.*
import sttp.tapir.ztapir.*
import zio.*
import zio.stm.ZSTM
import zio.stream.ZStream

case class App(
    migrationService: Database.Migration,
    auth: AuthService,
    repo: Repository,
    backend: Backend,
    wsBackend: WsBackend,
    repository: RoomRepository
) {

  def securityLogic(token: String) =
    (for {
      username <- auth.verifyJwt(token)
      user     <- repo.users.byUsername(username)
    } yield user)
      .orElseFail(Unauthorized(""))
      .someOrFail(Unauthorized(""))

  def register: FoxxyServerEndpoint = Endpoints.register
    .zServerLogic { req =>
      for {
        hash  <- auth.encryptPassword(req.password).orElseFail(BadRequest("Failed to hash password"))
        id    <- Random.nextUUID
        user   = User(id, req.name, hash)
        _     <- repo.users.c.insert(user).orElseFail(BadRequest("Failed to insert user"))
        token <- auth.generateJwt(user.name).orElseFail(BadRequest("Invalid credentials"))
      } yield token
    }

  def login: FoxxyServerEndpoint = Endpoints.login
    .zServerLogic { req =>
      for {
        user  <- repo.users.byUsername(req.name).orElseFail(BadRequest("Failed to find user")).someOrFail(Unauthorized(""))
        _     <- auth.verifyPassword(req.password, user.passwordHash).orElseFail(Unauthorized(""))
        token <- auth.generateJwt(user.name).orElseFail(BadRequest("Failed to generate token"))
      } yield token
    }

  def getTodos: FoxxyServerEndpoint = Endpoints.getTodos
    .zServerSecurityLogic(securityLogic)
    .serverLogic { user => _ =>
      for {
        todos <- repo.todoItems.byUserId(user.id).orElseFail(BadRequest("Invalid credentials"))
      } yield todos.map(x => TodoResponse(x.id, x.text, x.completed))
    }

  def addTodo: FoxxyServerEndpoint = Endpoints.addTodo
    .zServerSecurityLogic(securityLogic)
    .serverLogic { user => req =>
      for {
        id  <- Random.nextUUID
        todo = TodoItem(id, user.id, req.text, completed = false)
        _   <- repo.todoItems.c.insert(todo).orElseFail(BadRequest("Failed to insert todo"))
      } yield TodoResponse(todo.id, todo.text, todo.completed)
    }

  def updateTodo: FoxxyServerEndpoint = Endpoints.updateTodo
    .zServerSecurityLogic(securityLogic)
    .serverLogic { user => (id, req) =>
      for {
        todo <- repo.todoItems.c.find(id).orElseFail(BadRequest("Invalid todo id")).someOrFail(BadRequest("Invalid todo id"))
        _    <- repo.todoItems.c.update(todo.copy(completed = req.completed)).orElseFail(BadRequest("Failed to update todo"))
      } yield TodoResponse(todo.id, todo.text, todo.completed)
    }

  def removeTodo: FoxxyServerEndpoint = Endpoints.removeTodo
    .zServerSecurityLogic(securityLogic)
    .serverLogic { user => id =>
      for {
        todo <- repo.todoItems.c.find(id).orElseFail(BadRequest("Invalid todo id")).someOrFail(BadRequest("Invalid todo id"))
        _    <- repo.todoItems.c.delete(todo.id).orElseFail(BadRequest("Failed to delete todo"))
      } yield ()
    }

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
  def logic                                   = for {
    _    <- migrationService.reset.orDie *> migrationService.migrate.orDie
    id   <- Random.nextUUID
    _    <- repo.users.c.insert(User(id, "admin", "admin")).orDie
    a    <- backend
              .serve(
                List(
                  login,
                  register,
                  getTodos,
                  addTodo,
                  updateTodo,
                  removeTodo,
                  roomsHandler,
                  roomHandler,
                  createRoom,
                  deleteRoom,
                  joinRoom,
                  addVideo,
                  playVideo
                )
              )
              .fork
    room <- Domain.Room.empty("TestRoom")
    _    <- ZSTM.atomically(repository.addRoom(room))
    b    <- wsBackend.serve(roomConnectionHandler).fork
    _    <- Fiber.joinAll(List(a, b))
  } yield ()

}

object App {
  val live = ZLayer.derive[App]
}
