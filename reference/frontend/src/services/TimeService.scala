package foxxy.reference.frontend.services

import zio._

case class TimeService() {
  def getTime = Clock.currentDateTime
}