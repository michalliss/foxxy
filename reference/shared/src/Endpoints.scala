package foxxy.reference.shared

import foxxy.shared.BaseEndpoints.{publicEndpoint, secureEndpoint}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio._
import zio.json._

import java.util.UUID

object Endpoints {

  case class LoginRequest(name: String, password: String) derives JsonCodec
  def login = publicEndpoint.post
    .in("login")
    .in(jsonBody[LoginRequest])
    .out(jsonBody[String])

  case class RegisterRequest(name: String, password: String) derives JsonCodec
  def register = publicEndpoint.post
    .in("register")
    .in(jsonBody[RegisterRequest])
    .out(jsonBody[String])

  case class TodoResponse(id: UUID, text: String, completed: Boolean) derives JsonCodec
  type GetTodosResponse = List[TodoResponse]
  def getTodos = secureEndpoint.get
    .in("todos")
    .out(jsonBody[GetTodosResponse])

  case class AddTodoRequest(text: String) derives JsonCodec
  def addTodo = secureEndpoint.post
    .in("todos")
    .in(jsonBody[AddTodoRequest])
    .out(jsonBody[TodoResponse])

  def removeTodo = secureEndpoint.delete
    .in("todos")
    .in(path[UUID])

  case class UpdateTodoRequest(completed: Boolean) derives JsonCodec
  def updateTodo = secureEndpoint.patch
    .in("todos")
    .in(path[UUID])
    .in(jsonBody[UpdateTodoRequest])
}
