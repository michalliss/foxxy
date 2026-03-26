package foxxy.reference.backend

import foxxy.auth.*
import foxxy.backend.*
import foxxy.reference.shared.Endpoints
import foxxy.reference.shared.domain.*
import foxxy.repo.*
import foxxy.wsbackend.*
import scalasql.*
import scalasql.PostgresDialect.*
import zio.*
import zio.http.{ChannelEvent, Handler, WebSocketFrame}

case class App(
    migrationService: Database.Migration,
    auth: AuthService,
    backend: Backend,
    wsBackend: WsBackend,
    db: scalasql.DbClient
) {

  def securityLogic(token: String) =
    (for {
      username <- auth.verifyJwt(token)
      user     <- ZIO.attempt {
                    val a = db.transaction { tx =>
                      import scalasql.*
                      import PostgresDialect.*
                      tx.run(UserDB.select.filter(_.name === username)).headOption
                    }
                    a
                  }
    } yield user)

  def register = Endpoints.register.implement(req =>
    for {
      hash  <- auth.encryptPassword(req.password).orElseFail(Endpoints.BadRequest("Failed to hash password"))
      id    <- Random.nextUUID
      user   = User(id, req.name, hash)
      userDb = UserDB(user.id, user.name, user.passwordHash)
      _     <- ZIO
                 .attempt { db.transaction { tx => tx.run(UserDB.insert.values(userDb)) } }
                 .orElseFail(Endpoints.BadRequest("Failed to insert user"))
      token <- auth.generateJwt(user.name).orElseFail(Endpoints.BadRequest("Invalid credentials"))
    } yield token
  )

  def login = Endpoints.login.implement(req =>
    for {
      user  <- ZIO
                 .attempt { db.transaction { tx => tx.run(UserDB.select.filter(_.name === req.name)).headOption } }
                 .orElseFail(Endpoints.BadRequest("Failed to find user"))
                 .someOrFail(Endpoints.BadRequest("Invalid credentials"))
      _     <- auth.verifyPassword(req.password, user.passwordHash).orElseFail(Endpoints.BadRequest("Invalid credentials"))
      token <- auth.generateJwt(user.name).orElseFail(Endpoints.BadRequest("Failed to generate token"))
    } yield token
  )

  def roomHandler = Handler.webSocket(channel =>
    channel.receiveAll {
      case ChannelEvent.Read(WebSocketFrame.Text(text)) => ZIO.attempt { println(s"Received message: $text") }
      case _                                            => ZIO.unit
    }
  )

  def logic = for {
    _  <- migrationService.reset.orDie *> migrationService.migrate.orDie
    id <- Random.nextUUID
    _  <- ZIO.attempt {
            db.transaction { tx =>
              import scalasql.*
              import PostgresDialect.*
              tx.run(UserDB.insert.values(UserDB(id, "admin", "admin")))
            }
          }
    a  <- backend.serve(List(Endpoints.register, Endpoints.login), Chunk(register, login)).debug.fork
    b  <- wsBackend.serve(roomHandler).fork
    _  <- Fiber.joinAll(List(a, b))
  } yield ()

}

object App {
  val live = ZLayer.derive[App]
}
