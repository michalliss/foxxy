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
  val postgres        = Quill.Postgres.fromNamingStrategy(SnakeCase)
  val postgresFromEnv = for {
    props <- ZLayer.fromZIO(for {
               db_user     <- System.env("DB_USER").someOrElse("postgres")
               db_password <- System.env("DB_PASSWORD").someOrElse("postgres")
               db_host     <- System.env("DB_HOST").someOrElse("localhost")
               db_port     <- System.env("DB_PORT").someOrElse("5432")
             } yield {
               val props = new Properties()
               props.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource")
               props.setProperty("dataSource.user", db_user)
               props.setProperty("dataSource.password", db_password)
               props.setProperty(s"dataSource.serverName", db_host)
               props.setProperty(s"dataSource.portNumber", db_port)
               HikariDataSource(HikariConfig(props))
             })
    ds    <- Quill.DataSource.fromDataSource(props.get[HikariDataSource]) >+> Quill.Postgres.fromNamingStrategy(SnakeCase)
  } yield ds

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
