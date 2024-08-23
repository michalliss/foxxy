package foxxy.reference.backend

import foxxy.repo.*
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.*

case class Schema(quill: Quill.Postgres[SnakeCase]) {
  import quill.*

  inline def users     = createEntity[UserDB]("users")
  inline def todoItems = createEntity[TodoItemDB]("todo_items")
}

object Schema {
  val live = ZLayer.derive[Schema]
}
