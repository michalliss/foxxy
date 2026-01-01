package foxxy.reference.backend

import foxxy.auth.*
import foxxy.backend.{Backend, BackendConfig, WsBackend, WsBackendConfig}
import foxxy.repo.*
import zio.*
import zio.logging.slf4j.bridge.Slf4jBridge

import javax.sql.DataSource

object Main extends ZIOAppDefault {

  override def run = logic.exitCode

  def configurableLogic = ZIO
    .serviceWithZIO[App](_.logic)
    .provideSome[DataSource & BackendConfig & WsBackendConfig](
      Backend.live,
      Database.postgres,
      Database.Migration.live,
      Schema.live,
      AuthService.live,
      Repository.live,
      App.live,
      RoomRepository.live,
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
