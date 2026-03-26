package foxxy.reference.backend

import foxxy.auth.*
import foxxy.backend.{Backend, BackendConfig}
import foxxy.repo.*
import foxxy.wsbackend.{WsBackend, WsBackendConfig}
import zio.*
import zio.logging.slf4j.bridge.Slf4jBridge

import javax.sql.DataSource

object Main extends ZIOAppDefault {

  override def run = logic.exitCode

  def configurableLogic = ZIO
    .serviceWithZIO[App](_.logic)
    .provideSome[DataSource & BackendConfig & WsBackendConfig](
      Backend.live,
      Database.Migration.live,
      Database.client,
      AuthService.live,
      App.live,
      WsBackend.live
    )
    .provideSomeLayer(Slf4jBridge.initialize)

  def logic = configurableLogic
    .provide(
      Database.postgresFromEnv,
      BackendConfig.withPort(5004),
      WsBackendConfig.withPort(5005)
    )
    .debug
}
