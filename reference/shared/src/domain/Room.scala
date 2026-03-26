package foxxy.reference.shared.domain

import foxxy.reference.shared.domain.{PlaybackState, RoomContent, RoomQueue, RoomUser, Video}
import zio.*
import zio.schema.*

import java.util.UUID

case class Room(
    id: UUID,
    name: String,
    content: RoomContent,
    users: List[RoomUser],
    videoQueue: RoomQueue
) derives Schema {
  def addUser(user: RoomUser) = copy(users = user :: users)

  def removeUser(user: UUID) = copy(users = users.filterNot(_.id == user))

  def enqueue(video: Video) = copy(videoQueue = videoQueue.add(video))

  def playVideo(videoId: UUID) = videoQueue.videos
    .find(_.id == videoId)
    .map(video => copy(videoQueue = videoQueue.remove(videoId), content = RoomContent.WithMovie(video, PlaybackState.Idle)))

  def syncToUser(userId: UUID) = users
    .find(_.id == userId)
    .foreach(user => copy(content = content.updatePlaybackState(user.progress)))

  def updateUserProgress(userId: UUID, userProgress: PlaybackState) = {
    val updatedUsers = users.map {
      case user if user.id == userId => user.copy(progress = userProgress)
      case user                      => user
    }

    val leader = users.find(_.isLeader)

    val updatedContent = leader match {
      case Some(leader) if leader.id == userId =>
        content.updatePlaybackState(userProgress)
      case _                                   =>
        content
    }

    copy(users = updatedUsers, content = updatedContent)
  }

  def setLeader(userId: UUID) = {
    val updatedUsers = users.map {
      case user if user.id == userId => user.copy(isLeader = true)
      case user                      => user.copy(isLeader = false)
    }
    copy(users = updatedUsers)
  }
}

object Room {
  def empty(name: String) = Random.nextUUID.map(Room(_, name, RoomContent.Empty, List.empty, RoomQueue.empty))
}
