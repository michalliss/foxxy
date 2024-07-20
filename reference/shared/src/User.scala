package foxxy.reference.shared.domain

import java.util.UUID

case class User(id: UUID, name: String, passwordHash: String)

case class TodoItem(id: UUID, userId: UUID, text: String, completed: Boolean)