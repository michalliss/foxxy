package foxxy.reference.backend.test

import foxxy.reference.backend.Main
import foxxy.reference.shared.Endpoints
import foxxy.reference.shared.Endpoints.LoginRequest
import foxxy.shared.Unauthorized
import org.testcontainers.containers.PostgreSQLContainer
import sttp.client3.*
import sttp.model.Uri
import sttp.tapir.DecodeResult
import sttp.tapir.Endpoint
import sttp.tapir.client.sttp.SttpClientInterpreter
import zio.*
import zio.test.*
import foxxy.reference.backend.test.TestTools.TestBackend
import javax.sql.DataSource
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariConfig
import java.util.Properties

object TestTools {

  val postgresTest = ZLayer.scoped(
    ZIO
      .acquireRelease(ZIO.attempt {
        val container = new PostgreSQLContainer("postgres:16")
        container.start()
        container
      })(container => ZIO.succeed(container.stop()))
      .map(container => {
        val props = new Properties()
        props.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource")
        props.setProperty("dataSource.user", container.getUsername)
        props.setProperty("dataSource.password", container.getPassword)
        props.setProperty(s"dataSource.serverName", container.getHost)
        props.setProperty(s"dataSource.portNumber", container.getFirstMappedPort.toString)
        HikariDataSource(HikariConfig(props))
      })
  )

  def send[I, E, O](endpoint: Endpoint[Unit, I, E, O, Any], params: I): Task[DecodeResult[Either[E, O]]] =
    ZIO.attempt {
      val client = SttpClientInterpreter().toClient(endpoint, Some(Uri.parse("http://localhost:5004").right.get), backend)
      client(params)
    }

  case class TestBackend() {
    def send[I, E, O](endpoint: Endpoint[Unit, I, E, O, Any], params: I): Task[DecodeResult[Either[E, O]]] =
      ZIO.attempt {
        val client = SttpClientInterpreter().toClient(endpoint, Some(Uri.parse("http://localhost:5004").right.get), backend)
        client(params)
      }
  }

  object TestBackend {

    val live = postgresTest >>> ZLayer.scoped(for {
      testBackend <- ZIO.succeed(TestBackend())
      _           <- Main.logicWithoutDb.forkScoped
      _           <- testBackend.send(Endpoints.login, LoginRequest("admin", "admin")).retry(Schedule.fixed(1.second))
    } yield TestBackend())
  }

  val backend = HttpClientSyncBackend()

}

object BasicAuthSpec extends ZIOSpecDefault {

  def spec = suite("Basic auth suite")(
    test("Login with invalid credentials should return Unauthorized") {
      for {
        testBackend <- ZIO.service[TestBackend]
        result      <- testBackend.send(Endpoints.login, LoginRequest("admin", "admin"))
      } yield assert(result)(Assertion.equalTo(DecodeResult.Value(Left(Unauthorized("")))))
    },
    test("Login with empty credentials should return Unauthorized") {
      for {
        testBackend <- ZIO.service[TestBackend]
        result      <- testBackend.send(Endpoints.login, LoginRequest("", ""))
      } yield assert(result)(Assertion.equalTo(DecodeResult.Value(Left(Unauthorized("")))))
    }
  ).provideShared(TestBackend.live) @@ TestAspect.withLiveClock
}
