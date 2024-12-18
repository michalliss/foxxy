package foxxy.reference.backend

import foxxy.reference.shared.domain.*
import foxxy.repo.*
import io.getquill.*
import zio.*

import java.util.UUID
import javax.sql.DataSource

case class Repository(schema: Schema, dataSource: DataSource) {
  import UserDB.given
  import TodoItemDB.given

  import schema.quill.*

  object users {
    def byUsername(username: String) = run(schema.users.filter(_.name == lift(username)).take(1)).map(_.headOption.map(_.to))
    def c                            = crudMap(dataSource, schema.users)
  }

  object todoItems {
    def c                      = crudMap(dataSource, schema.todoItems)
    def byUserId(userId: UUID) = run(schema.todoItems.filter(_.userId == lift(userId))).map(_.map(_.to))
  }
}

object Repository {
  val live = ZLayer.derive[Repository]
}
