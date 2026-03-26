package foxxy.reference.shared.domain

import zio.*
import zio.schema.*
enum PlaybackState derives Schema {
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
