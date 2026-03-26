package foxxy.reference.shared.domain

import foxxy.reference.shared.domain.Video
import zio.*
import zio.schema.*

import java.util.UUID

case class RoomQueue(videos: List[Video]) derives Schema {
  def add(video: Video)     = copy(videos = video :: videos)
  def remove(videoId: UUID) = copy(videos = videos.filterNot(_.id == videoId))
}

object RoomQueue {
  def empty = RoomQueue(List.empty)
}
