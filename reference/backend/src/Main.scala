package foxxy.reference.backend

import foxxy.auth.*
import foxxy.repo.*
import zio.*
import javax.sql.DataSource

object Main extends ZIOAppDefault {

  override def run = logic.exitCode

  def logicWithoutDb = ZIO
    .serviceWithZIO[App](_.logic)
    .provideSome[DataSource](Database.postgres, Database.Migration.live, Schema.live, AuthService.live, Repository.live, App.live)

  def logic = logicWithoutDb.provide(Database.postgresFromEnv)
}
