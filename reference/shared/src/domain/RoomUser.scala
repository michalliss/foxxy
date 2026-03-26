package foxxy.reference.shared.domain

import foxxy.reference.shared.domain.PlaybackState
import zio.*
import zio.schema.*

import java.util.UUID

case class RoomUser(id: UUID, name: String, progress: PlaybackState, isLeader: Boolean, syncWindow: Duration) derives Schema {}

object RoomUser {
  def create(name: String) = Random.nextUUID.map(RoomUser(_, name, PlaybackState.Idle, false, Duration.fromSeconds(2)))
}
