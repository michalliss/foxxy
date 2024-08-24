package foxxy.reference.backend

import foxxy.auth.*
import foxxy.backend.{Backend, BackendConfig}
import foxxy.repo.*
import zio.*
import zio.logging.slf4j.bridge.Slf4jBridge

import javax.sql.DataSource

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
      App.live
    )
    .provideSomeLayer(Slf4jBridge.initialize)

  def logic = configurableLogic.provide(Database.postgresFromEnv, BackendConfig.withPort(5004))
}
