package foxxy.reference.backend

import foxxy.repo._
import io.getquill._
import io.getquill.jdbczio.Quill
import zio._

case class Schema(quill: Quill.Postgres[SnakeCase]) {
  import quill.*

  inline def users     = createEntity[UserDB]("users")
  inline def todoItems = createEntity[TodoItemDB]("todo_items")
}

object Schema {
  val live = ZLayer.derive[Schema]
}

