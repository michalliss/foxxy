package foxxy.reference.backend

import foxxy.reference.shared.domain._
import foxxy.repo._

import java.util.UUID

case class UserDB(id: UUID, name: String, passwordHash: String)

object UserDB {
  given MapTo[UserDB, User] = a => User(a.id, a.name, a.passwordHash)
  given MapTo[User, UserDB] = a => UserDB(a.id, a.name, a.passwordHash)
  given WithId[UserDB] with { extension (x: UserDB) inline def id: UUID = x.id }
  given WithId[User] with   { extension (x: User) inline def id: UUID = x.id   }
}
