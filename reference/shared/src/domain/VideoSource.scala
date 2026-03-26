package foxxy.reference.shared.domain

import zio.*
import zio.schema.*

sealed trait VideoSource derives Schema {
  def video_url = this match
    case VideoSource.RawFile(url) => url
    case yt: VideoSource.Youtube  => yt.youtube_url

}

object VideoSource {
  case class RawFile(url: String) extends VideoSource derives Schema
  case class Youtube(id: String)  extends VideoSource derives Schema {
    def youtube_url = s"https://www.youtube.com/watch?v=${id}"
  }
}
