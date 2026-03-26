package foxxy.reference.shared.domain

import foxxy.reference.shared.domain.{PlaybackState, Video}
import zio.*
import zio.schema.*

enum RoomContent derives Schema:
  case WithMovie(video: Video, playback: PlaybackState)
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
