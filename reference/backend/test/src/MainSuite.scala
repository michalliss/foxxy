package foxxy.reference.backend.test

import foxxy.reference.shared.Endpoints
import foxxy.reference.shared.Endpoints.LoginRequest
import foxxy.reference.shared.Endpoints.RegisterRequest
import foxxy.shared.Unauthorized
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Value
import zio.*
import zio.test.*
import foxxy.testing.*
import foxxy.reference.backend.Main
import foxxy.backend.BackendConfig
import javax.sql.DataSource

object EndToEndSpec extends ZIOSpecDefault {
  def spec = suite("End to end tests")(
    suite("Unauthorized login test")(
      test("Login with invalid credentials should return Unauthorized") {
        for {
          result <- TestClient.send(Endpoints.login, LoginRequest("admin", "admin"))
        } yield assert(result)(Assertion.equalTo(DecodeResult.Value(Left(Unauthorized("")))))
      },
      test("Login with empty credentials should return Unauthorized") {
        for {
          result <- TestClient.send(Endpoints.login, LoginRequest("", ""))
        } yield assert(result)(Assertion.equalTo(DecodeResult.Value(Left(Unauthorized("")))))
      }
    ),
    suite("Register and login test")(
      test("Register and then login with valid credentials should return Ok") {
        for {
          _      <- TestClient.send(Endpoints.register, RegisterRequest("test", "test"))
          result <- TestClient.send(Endpoints.login, LoginRequest("test", "test"))
        } yield assert(result.match { case Value(v) => v.right.get })(Assertion.isNonEmptyString)
      }
    )
  ).provide(
    PostgresContainer.layer,
    TestClient.startOnFreePort(
      port => Main.configurableLogic.provideSome[DataSource](BackendConfig.withPort(port)),
      client => client.send(Endpoints.login, LoginRequest("admin", "admin")).unit
    )
  ) @@ TestAspect.withLiveClock @@ TestAspect.sequential @@ TestAspect.silentLogging
}
