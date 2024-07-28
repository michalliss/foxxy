package foxxy.reference.backend

import foxxy.auth.*
import foxxy.backend.*
import foxxy.repo.*
import foxxy.shared.*
import foxxy.reference.shared.domain.*
import sttp.tapir.*
import sttp.tapir.ztapir.*
import zio.*

import java.util.UUID
import foxxy.reference.shared.domain.User
import foxxy.reference.shared.Endpoints
import foxxy.reference.shared.Endpoints.TodoResponse

case class App(migrationService: Database.Migration, auth: AuthService, repo: Repository) {

  def securityLogic(token: String) =
    (for {
      username <- auth.verifyJwt(token)
      user     <- repo.users.byUsername(username)
    } yield user)
      .orElseFail(Unauthorized(""))
      .someOrFail(Unauthorized(""))

  def register: ServerEndpoint = Endpoints.register
    .zServerLogic { req =>
      for {
        hash  <- auth.encryptPassword(req.password).orElseFail(BadRequest("Failed to hash password"))
        user   = User(java.util.UUID.randomUUID(), req.name, hash)
        _     <- repo.users.c.insert(user).orElseFail(BadRequest("Failed to insert user"))
        token <- auth.generateJwt(user.name).orElseFail(BadRequest("Invalid credentials"))
      } yield token
    }

  def login: ServerEndpoint = Endpoints.login
    .zServerLogic { req =>
      for {
        user  <- repo.users.byUsername(req.name).orElseFail(BadRequest("Failed to find user")).someOrFail(Unauthorized(""))
        _     <- auth.verifyPassword(req.password, user.passwordHash).orElseFail(Unauthorized(""))
        token <- auth.generateJwt(user.name).orElseFail(BadRequest("Failed to generate token"))
      } yield token
    }

  def getTodos: ServerEndpoint = Endpoints.getTodos
    .zServerSecurityLogic(securityLogic)
    .serverLogic { user => _ =>
      for {
        todos <- repo.todoItems.byUserId(user.id).orElseFail(BadRequest("Invalid credentials"))
      } yield todos.map(x => TodoResponse(x.id, x.text, x.completed))
    }

  def addTodo: ServerEndpoint = Endpoints.addTodo
    .zServerSecurityLogic(securityLogic)
    .serverLogic { user => req =>
      val todo = TodoItem(UUID.randomUUID(), user.id, req.text, completed = false)
      for {
        _ <- repo.todoItems.c.insert(todo).orElseFail(BadRequest("Failed to insert todo"))
      } yield TodoResponse(todo.id, todo.text, todo.completed)
    }

  def updateTodo: ServerEndpoint = Endpoints.updateTodo
    .zServerSecurityLogic(securityLogic)
    .serverLogic { user => (id, req) =>
      for {
        todo <- repo.todoItems.c.find(id).orElseFail(BadRequest("Invalid todo id")).someOrFail(BadRequest("Invalid todo id"))
        _    <- repo.todoItems.c.update(todo.copy(completed = req.completed)).orElseFail(BadRequest("Failed to update todo"))
      } yield TodoResponse(todo.id, todo.text, todo.completed)
    }

  def removeTodo: ServerEndpoint = Endpoints.removeTodo
    .zServerSecurityLogic(securityLogic)
    .serverLogic { user => id =>
      for {
        todo <- repo.todoItems.c.find(id).orElseFail(BadRequest("Invalid todo id")).someOrFail(BadRequest("Invalid todo id"))
        _    <- repo.todoItems.c.delete(todo.id).orElseFail(BadRequest("Failed to delete todo"))
      } yield ()
    }

  def logic = for {
    _ <- migrationService.reset.orDie *> migrationService.migrate.orDie
    _ <- repo.users.c.insert(User(UUID.randomUUID(), "admin", "admin")).orDie
    _ <- Backend(5004, List(login, register, getTodos, addTodo, updateTodo, removeTodo)).serve
  } yield ()
}

object App {
  val live = ZLayer.derive[App]
}
