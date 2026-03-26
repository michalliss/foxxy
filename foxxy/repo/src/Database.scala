package foxxy.repo

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.flywaydb.core.Flyway
import zio.*

import java.util.Properties
import javax.sql.DataSource

object Database {
  import scalasql.*
  import scalasql.PostgresDialect.*
  val postgresFromEnv: ZLayer[Any, SecurityException, DataSource] = for {
    ds <- ZLayer.fromZIO(for {
            db_user     <- System.env("DB_USER").someOrElse("postgres")
            db_password <- System.env("DB_PASSWORD").someOrElse("postgres")
            db_host     <- System.env("DB_HOST").someOrElse("localhost")
            db_port     <- System.env("DB_PORT").someOrElse("54322")
          } yield {
            val props = new Properties()
            props.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource")
            props.setProperty("dataSource.user", db_user)
            props.setProperty("dataSource.password", db_password)
            props.setProperty(s"dataSource.serverName", db_host)
            props.setProperty(s"dataSource.portNumber", db_port)
            HikariDataSource(HikariConfig(props))
          })
  } yield ds

  val client: ZLayer[DataSource, Nothing, scalasql.core.DbClient] =
    ZLayer.service[DataSource].flatMap(ds => ZLayer.succeed(scalasql.core.DbClient.DataSource(ds.get)))

  final case class Migration(dataSource: DataSource) {

    val migrate: Task[Unit] =
      for {
        flyway <- loadFlyway
        _      <- ZIO.attempt(flyway.migrate())
      } yield ()

    val reset: Task[Unit] =
      for {
        _      <- ZIO.log("RESETTING DATABASE!")
        flyway <- loadFlyway
        _      <- ZIO.attempt(flyway.clean())
        _      <- ZIO.attempt(flyway.migrate())
      } yield ()

    private lazy val loadFlyway: Task[Flyway] =
      ZIO.attempt {
        Flyway
          .configure()
          .dataSource(dataSource)
          .baselineOnMigrate(true)
          .baselineVersion("0")
          .cleanDisabled(false)
          .load()
      }

  }

  object Migration {
    val live = ZLayer.derive[Migration]
  }

}
