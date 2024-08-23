package foxxy.reference.backend

import foxxy.reference.shared.domain._
import foxxy.repo._

import java.util.UUID

case class TodoItemDB(id: UUID, userId: UUID, text: String, completed: Boolean)

object TodoItemDB {
  given MapTo[TodoItemDB, TodoItem] = a => TodoItem(a.id, a.userId, a.text, a.completed)
  given MapTo[TodoItem, TodoItemDB] = a => TodoItemDB(a.id, a.userId, a.text, a.completed)
  given WithId[TodoItemDB] with { extension (x: TodoItemDB) inline def id: UUID = x.id }
  given WithId[TodoItem] with   { extension (x: TodoItem) inline def id: UUID = x.id   }
}