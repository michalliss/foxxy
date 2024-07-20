package foxxy.reference.frontend.services

import zio.*

case class TimeService() {
  def getTime = Clock.currentDateTime
}