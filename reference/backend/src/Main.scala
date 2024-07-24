package foxxy.reference.backend

import foxxy.auth.*
import foxxy.repo.*
import zio.*

object Main extends ZIOAppDefault {
  override def run = logic

  def logic = ZIO
    .serviceWithZIO[App](_.logic)
    .provide(
      App.live,
      Database.postgresFromEnv,
      Database.Migration.live,
      AuthService.live,
      Schema.live,
      Repository.live
    )
}
