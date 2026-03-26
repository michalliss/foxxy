package foxxy.reference.backend

import scalasql.*
import scalasql.namedtuples.SimpleTable

import java.util.UUID

case class UserDB(id: UUID, name: String, passwordHash: String)
object UserDB extends SimpleTable[UserDB] {
  override def tableName: String = "users"
}
