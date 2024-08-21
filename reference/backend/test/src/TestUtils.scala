package foxxy.reference.backend.test

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import foxxy.backend.BackendConfig
import foxxy.reference.backend.Main
import foxxy.reference.shared.Endpoints
import foxxy.reference.shared.Endpoints.LoginRequest
import org.testcontainers.containers.PostgreSQLContainer
import sttp.client3.*
import sttp.model.Uri
import sttp.tapir.DecodeResult
import sttp.tapir.Endpoint
import sttp.tapir.client.sttp.SttpClientInterpreter
import zio.*
import scala.collection.JavaConverters._
import scala.collection.Map

import java.net.ServerSocket
import java.util.Properties
import scala.util.Try
import scala.util.Using

object TestUtils {

  def mapToProps(map: scala.collection.Map[String, String]): Properties = {
    val props = new Properties()
    props.putAll(map.asJava)
    props
  }

  val postgresTest = ZLayer.scoped {
    val startContainer = ZIO.attempt { val container = new PostgreSQLContainer("postgres:16"); container.start(); container }
    val endContainer   = (container: PostgreSQLContainer[?]) => ZIO.succeed(container.stop())

    ZIO
      .acquireRelease(startContainer)(endContainer)
      .map(container => {
        val props = Map(
          "dataSourceClassName"     -> "org.postgresql.ds.PGSimpleDataSource",
          "dataSource.databaseName" -> container.getDatabaseName,
          "dataSource.user"         -> container.getUsername,
          "dataSource.password"     -> container.getPassword,
          "dataSource.serverName"   -> container.getHost,
          "dataSource.portNumber"   -> container.getFirstMappedPort.toString
        )
        HikariDataSource(HikariConfig(mapToProps(props)))
      })
  }

  case class TestBackend(port: Int) {
    def send[I, E, O](endpoint: Endpoint[Unit, I, E, O, Any], params: I): Task[DecodeResult[Either[E, O]]] =
      ZIO.attempt {
        val client = SttpClientInterpreter().toClient(endpoint, Some(Uri.parse(s"http://localhost:${port}").right.get), backend)
        client(params)
      }
  }

  object TestBackend {

    def send[I, E, O](endpoint: Endpoint[Unit, I, E, O, Any], params: I) = ZIO.serviceWithZIO[TestBackend](_.send(endpoint, params))

    def findFreePort(): Try[Int] = Using(new ServerSocket(0))(_.getLocalPort)

    val live = postgresTest >>> ZLayer.scoped(for {
      randomPort  <- ZIO.attempt(findFreePort().get)
      testBackend <- ZIO.succeed(TestBackend(randomPort))
      _           <- Main.configurableLogic.provideSome[javax.sql.DataSource](BackendConfig.withPort(randomPort)).forkScoped
      _           <- testBackend.send(Endpoints.login, LoginRequest("admin", "admin")).retry(Schedule.fixed(1.second))
    } yield testBackend)
  }

  val backend = HttpClientSyncBackend()

}
