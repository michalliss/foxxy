package foxxy.reference.backend

import foxxy.auth.*
import foxxy.repo.*
import zio.*
import javax.sql.DataSource
import foxxy.backend.BackendConfig
import foxxy.backend.Backend

object Main extends ZIOAppDefault {

  override def run = logic.exitCode

  def configurableLogic = ZIO
    .serviceWithZIO[App](_.logic)
    .provideSome[DataSource & BackendConfig](
      Backend.live,
      Database.postgres, 
      Database.Migration.live, 
      Schema.live, 
      AuthService.live, 
      Repository.live, 
      App.live)

  def logic = configurableLogic.provide(Database.postgresFromEnv, BackendConfig.withPort(5004))
}
