package foxxy.repo

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.*
import io.getquill.jdbczio.Quill
import org.flywaydb.core.Flyway
import zio.*

import java.sql.SQLException
import java.util.{Properties, UUID}
import javax.sql.DataSource

object Database {
  val postgresFromEnv = {
    Quill.DataSource.fromDataSource({
      val props  = new Properties()
      props.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource")
      props.setProperty("dataSource.user", s"${scala.util.Properties.envOrElse("DB_USER", "postgres")}")
      props.setProperty("dataSource.password", s"${scala.util.Properties.envOrElse("DB_PASSWORD", "postgres")}")
      props.setProperty(s"dataSource.serverName", s"${scala.util.Properties.envOrElse("DB_HOST", "localhost")}")
      props.setProperty(s"dataSource.portNumber", s"${scala.util.Properties.envOrElse("DB_PORT", "5432")}")
      val config = new HikariConfig(props)
      val ds     = new HikariDataSource(config)
      ds
    }) >+>
      Quill.Postgres.fromNamingStrategy(SnakeCase)
  }

  final case class Migration(dataSource: DataSource) {

    val migrate: Task[Unit] =
      for {
        flyway <- loadFlyway
        _      <- Console.printLine(flyway.getConfiguration.getLocations.toList)
        _      <- ZIO.attempt(flyway.migrate())
      } yield ()

    val reset: Task[Unit] =
      for {
        _      <- ZIO.debug("RESETTING DATABASE!")
        flyway <- loadFlyway
        _      <- Console.printLine(flyway.getConfiguration.getLocations.toList)
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

trait MapTo[A, B]:
  extension (a: A) def to: B

trait WithId[T]:
  extension (x: T) inline def id: UUID

trait Crud[TDb, TDomain]:
  def insert(x: TDomain): ZIO[Any, SQLException, Long]
  def delete(x: UUID): ZIO[Any, SQLException, Unit]
  def find(x: UUID): ZIO[Any, SQLException, Option[TDomain]]
  def update(x: TDomain): ZIO[Any, SQLException, Unit]

object MyContext extends PostgresZioJdbcContext(SnakeCase)

inline def createEntity[TDb](inline name: String) = {
  quote { querySchema[TDb](name) }
}

inline def crud[TDb, TDomain](
    ds: DataSource,
    inline s: Quoted[EntityQuery[TDb]]
)(implicit ev: MapTo[TDb, TDomain], ev2: MapTo[TDomain, TDb], ev3: WithId[TDomain], ev4: WithId[TDb]) = {
  import MyContext.*

  new Crud[TDb, TDomain] {
    override def find(x: UUID): ZIO[Any, SQLException, Option[TDomain]] =
      run(s.filter(_.id == lift(x)))
        .map(_.headOption)
        .map(_.map(_.to))
        .provide(ZLayer.succeed(ds))

    override def update(x: TDomain): ZIO[Any, SQLException, Unit] =
      run(s.filter(_.id == lift(x.id)).updateValue(lift(x.to))).unit
        .provide(ZLayer.succeed(ds))

    override def insert(x: TDomain): ZIO[Any, SQLException, Long] = run(s.insertValue(lift(x.to)))
      .provide(ZLayer.succeed(ds))

    override def delete(x: UUID): ZIO[Any, SQLException, Unit] =
      run(s.filter(_.id == lift(x)).delete)
        .filterOrFail(_ == 1)(new SQLException("Failed to delete"))
        .unit
        .provide(ZLayer.succeed(ds))
  }
}
