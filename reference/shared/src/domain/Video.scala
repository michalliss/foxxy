package foxxy.reference.shared.domain

import foxxy.reference.shared.domain.VideoSource
import zio.*
import zio.schema.*

import java.util.UUID

case class Video(id: UUID, source: VideoSource) derives Schema {}

object Video {
  def create(url: String) = for {
    id     <- Random.nextUUID
    source <- ZIO.fromOption(parseUrl(url))
  } yield Video(id, source)

  def parseUrl(url: String): Option[VideoSource] = {
    val youtubeRgx =
      """https?://(?:[0-9a-zA-Z-]+\.)?(?:youtu\.be/|youtube\.com\S*[^\w\-\s])([\w \-]{11})(?=[^\w\-]|$)(?![?=&+%\w]*(?:[\'"][^<>]*>|</a>))[?=&+%\w-]*""".r

    url match {
      case youtubeRgx(a) => Some(VideoSource.Youtube(a))
      case _             => Some(VideoSource.RawFile(url))
    }

  }
}
