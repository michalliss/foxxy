package foxxy.reference.shared.domain

import zio.schema.*

import java.util.UUID

case class User(id: UUID, name: String, passwordHash: String) derives Schema
