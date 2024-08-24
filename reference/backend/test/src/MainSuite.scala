package foxxy.reference.backend.test

import foxxy.backend.BackendConfig
import foxxy.reference.backend.Main
import foxxy.reference.shared.Endpoints
import foxxy.reference.shared.Endpoints.{LoginRequest, RegisterRequest}
import foxxy.shared.Unauthorized
import foxxy.testing.*
import zio.*
import zio.test.*
import zio.test.Assertion.*

import javax.sql.DataSource

object EndToEndSpec extends FoxxySpec {
  def spec = suite("End to end tests")(
    suite("Unauthorized login test")(
      test("Login with invalid credentials should return Unauthorized") {
        for {
          result <- TestClient.send(Endpoints.login, LoginRequest("admin", "admin"))
        } yield assert(result)(isLeft(equalTo(Unauthorized(""))))
      },
      test("Login with empty credentials should return Unauthorized") {
        for {
          result <- TestClient.send(Endpoints.login, LoginRequest("", ""))
        } yield assert(result)(isLeft(equalTo(Unauthorized(""))))
      }
    ),
    suite("Register and login test")(
      test("Register and then login with valid credentials should return Ok") {
        for {
          _      <- TestClient.send(Endpoints.register, RegisterRequest("test", "test"))
          result <- TestClient.send(Endpoints.login, LoginRequest("test", "test"))
        } yield assert(result)(isRight(isNonEmptyString))
      }
    )
  ).provide(
    PostgresContainer.layer,
    TestClient.startOnFreePort(
      port => Main.configurableLogic.provideSome[DataSource](BackendConfig.withPort(port)),
      client => client.send(Endpoints.login, LoginRequest("admin", "admin")).unit
    )
  ) @@ TestAspect.withLiveClock @@ TestAspect.silentLogging
}
