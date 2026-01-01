package foxxy.reference.backend

import foxxy.reference.shared.Domain
import zio.*
import zio.stm.*

import java.util.UUID

case class RoomRepository(rooms: TMap[UUID, Domain.Room], videos: TMap[UUID, Domain.Video]) {
  def addRoom(room: Domain.Room)    = rooms.put(room.id, room)
  def updateRoom(room: Domain.Room) = rooms.put(room.id, room)
  def roomById(id: UUID)            = rooms.get(id)
  def allRooms                      = rooms.values
  def removeRoom(id: UUID)          = rooms.delete(id)

  def addVideo(video: Domain.Video) = videos.put(video.id, video)
  def removeVideo(id: UUID)         = videos.delete(id)
  def videoById(id: UUID)           = videos.get(id)
}

object RoomRepository {
  def live = ZLayer {
    ZSTM.atomically(
      for {
        rooms  <- TMap.empty[UUID, Domain.Room]
        videos <- TMap.empty[UUID, Domain.Video]
      } yield RoomRepository(rooms, videos)
    )
  }
}
