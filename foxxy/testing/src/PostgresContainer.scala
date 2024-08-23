package foxxy.testing

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.testcontainers.containers.PostgreSQLContainer
import zio.*

import java.util.Properties
import scala.collection.JavaConverters.*
import scala.collection.Map

object PostgresContainer {
  val layer = ZLayer.scoped {
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

  private def mapToProps(map: scala.collection.Map[String, String]): Properties = {
    val props = new Properties()
    props.putAll(map.asJava)
    props
  }
}
