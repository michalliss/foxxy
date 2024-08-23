package foxxy.reference.backend

import foxxy.reference.shared.domain._
import foxxy.repo._
import io.getquill._
import zio._

import java.util.UUID
import javax.sql.DataSource

case class Repository(schema: Schema, dataSource: DataSource) {
  import UserDB.given
  import TodoItemDB.given

  import schema.quill.*

  object users {
    def byUsername(username: String) = run(schema.users.filter(_.name == lift(username)).take(1)).map(_.headOption.map(_.to))
    def c                            = crud(dataSource, schema.users)
  }

  object todoItems {
    def c                      = crud(dataSource, schema.todoItems)
    def byUserId(userId: UUID) = run(schema.todoItems.filter(_.userId == lift(userId))).map(_.map(_.to))
  }
}

object Repository {
  val live = ZLayer.derive[Repository]
}
