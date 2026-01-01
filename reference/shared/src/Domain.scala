package foxxy.reference.shared

import zio.*
import zio.json.JsonCodec

import java.util.UUID

object Domain {

  case class Room(
      id: UUID,
      name: String,
      content: RoomContent,
      users: List[RoomUser],
      videoQueue: RoomQueue
  ) derives JsonCodec {
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

  case class RoomQueue(videos: List[Video]) derives JsonCodec {
    def add(video: Video)     = copy(videos = video :: videos)
    def remove(videoId: UUID) = copy(videos = videos.filterNot(_.id == videoId))
  }

  object RoomQueue {
    def empty = RoomQueue(List.empty)
  }

  enum RoomContent derives JsonCodec:
    case WithMovie(video: Domain.Video, playback: PlaybackState)
    case Empty

    def videoOpt: Option[Video] = this match {
      case WithMovie(v, _) => Some(v)
      case Empty           => None
    }

    def resume = this match {
      case WithMovie(v, p) => WithMovie(v, p.resume)
      case _               => this
    }

    def pause = this match {
      case WithMovie(v, p) => WithMovie(v, p.pause)
      case _               => this
    }

    def stop = this match {
      case WithMovie(v, p) => WithMovie(v, p.stop)
      case _               => this
    }

    def updateTime(time: Duration) = this match {
      case WithMovie(v, p) => WithMovie(v, p.updateTime(time))
      case _               => this
    }

    def updatePlaybackState(playbackState: PlaybackState) = this match {
      case WithMovie(v, _) => WithMovie(v, playbackState)
      case _               => this
    }

  enum PlaybackState derives JsonCodec {
    case Playing(time: Duration)
    case Paused(time: Duration)
    case Idle

    def resume = this match {
      case Playing(_) => this
      case Paused(t)  => Playing(t)
      case Idle       => this
    }

    def pause = this match {
      case Playing(t) => Paused(t)
      case Paused(_)  => this
      case Idle       => this
    }

    def stop = Idle

    def updateTime(time: Duration) = this match {
      case Playing(_) => Playing(time)
      case Paused(_)  => Paused(time)
      case Idle       => this
    }

    def isDifferentFrom(other: PlaybackState, epsilon: Duration) = (this, other) match {
      case (Idle, Idle)               => false
      case (Playing(t1), Playing(t2)) => t1.minus(t2).abs() > epsilon
      case (Paused(t1), Paused(t2))   => t1.minus(t2).abs() > epsilon
      case _                          => true
    }
  }

  case class Video(id: UUID, source: VideoSource) derives JsonCodec {}

  sealed trait VideoSource derives JsonCodec {
    def video_url = this match
      case VideoSource.RawFile(url) => url
      case yt: VideoSource.Youtube  => yt.youtube_url

  }

  object VideoSource {
    case class RawFile(url: String) extends VideoSource derives JsonCodec
    case class Youtube(id: String)  extends VideoSource derives JsonCodec {
      def youtube_url = s"https://www.youtube.com/watch?v=${id}"
    }
  }

  object Video {
    def create(url: String) = for {
      id     <- Random.nextUUID
      source <- ZIO.fromOption(parseUrl(url))
    } yield Video(id, source)

    def parseUrl(url: String): Option[VideoSource] = {
      import sttp.model.Uri
      val youtubeRgx =
        """https?://(?:[0-9a-zA-Z-]+\.)?(?:youtu\.be/|youtube\.com\S*[^\w\-\s])([\w \-]{11})(?=[^\w\-]|$)(?![?=&+%\w]*(?:[\'"][^<>]*>|</a>))[?=&+%\w-]*""".r

      Uri.parse(url) match
        case Left(value)  => None
        case Right(value) => {
          value.toString match {
            case youtubeRgx(a) => Some(VideoSource.Youtube(a))
            case _             => Some(VideoSource.RawFile(url))
          }
        }
    }
  }

  case class RoomUser(id: UUID, name: String, progress: PlaybackState, isLeader: Boolean, syncWindow: Duration) derives JsonCodec {}

  object RoomUser {
    def create(name: String) = Random.nextUUID.map(RoomUser(_, name, PlaybackState.Idle, false, Duration.fromSeconds(2)))
  }
}
