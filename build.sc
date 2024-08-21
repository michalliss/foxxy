import mill._
import mill.scalajslib.api.ModuleKind
import scalalib._
import scalajslib._
import publish._

import $ivy.`com.lihaoyi::mill-contrib-sonatypecentral:`
import mill.contrib.sonatypecentral.SonatypeCentralPublishModule

object config {
  val scalaVersion = "3.4.2"
}

trait FoxxyPublish extends PublishModule with SonatypeCentralPublishModule {

  def publishVersion = "0.0.1"

  def pomSettings = PomSettings(
    description = "Hello",
    organization = "io.github.michalliss",
    url = "https://github.com/michalliss/foxxy",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("michalliss", "foxxy"),
    developers = Seq(
      Developer("michalliss", "Michał Liss", "https://github.com/michalliss")
    )
  )
}

trait AppScalaModule extends ScalaModule {
  def scalaVersion  = config.scalaVersion
  def scalacOptions = Seq("-Wunused:all")
}

trait AppScalaJSModule extends AppScalaModule with ScalaJSModule {
  def scalaJSVersion = "1.16.0"
  def scalacOptions  = Seq("-Wunused:all")
}

object external {

  def tapir = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-core:1.10.12",
    ivy"com.softwaremill.sttp.tapir::tapir-json-zio:1.10.12"
  )

  def tapir_js = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-core::1.10.12",
    ivy"com.softwaremill.sttp.tapir::tapir-json-zio::1.10.12"
  )

  def tapirServer = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-http4s-server-zio:1.10.12",
    ivy"com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.10.12"
  )

  def zio = Agg(
    ivy"dev.zio::zio:2.1.6",
    ivy"dev.zio::zio-streams:2.1.6",
    ivy"dev.zio::zio-json:0.7.1"
  )

  def zio_js = Agg(
    ivy"dev.zio::zio::2.1.6",
    ivy"dev.zio::zio-streams::2.1.6",
    ivy"dev.zio::zio-json::0.7.1"
  )

  def quill = Agg(
    ivy"io.getquill::quill-jdbc-zio:4.8.5",
    ivy"org.postgresql:postgresql:42.7.3"
  )

  def flyway = Agg(
    ivy"org.flywaydb:flyway-core:10.15.2",
    ivy"org.flywaydb:flyway-database-postgresql:10.15.2",
  )

  def http4s = Agg(
    ivy"org.http4s::http4s-dsl:0.23.27",
    ivy"org.http4s::http4s-blaze-server:0.23.16"
  )

  def auth = Agg(
    ivy"com.password4j:password4j:1.8.2",
    ivy"com.auth0:java-jwt::4.4.0"
  )

  def frontend = zio_js ++ Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-sttp-client::1.10.12",
    ivy"com.softwaremill.sttp.client3::core::3.9.7",
    ivy"com.softwaremill.sttp.client3::zio::3.9.7",
    ivy"com.raquo::laminar::17.0.0",
    ivy"com.raquo::waypoint::8.0.0",
    ivy"io.laminext::websocket::0.17.0",
    ivy"be.doeraene::web-components-ui5::1.21.2",
    ivy"org.scala-js::scalajs-java-securerandom::1.0.0".withDottyCompat(
      config.scalaVersion
    ),
    ivy"io.github.cquiroz::scala-java-time::2.6.0",
    ivy"io.github.kitlangton::animus::0.6.2"
  )
}

object root extends RootModule {

  def devBackend() = T.command {
    reference.backend.runBackground()
  }

  def devFrontend() = T.command {
    reference.`frontend_vite`.compile()
  }

  object foxxy extends Module {

    object auth extends AppScalaModule with FoxxyPublish {
      override def ivyDeps = external.zio ++ external.auth
    }

    object backend extends AppScalaModule with FoxxyPublish {
      override def moduleDeps = Seq(shared.jvm)
      override def ivyDeps    =
        external.zio ++ external.tapir ++ external.tapirServer ++ external.http4s
    }

    object repo extends AppScalaModule with FoxxyPublish {
      override def ivyDeps = external.quill ++ external.flyway
    }

    object shared extends Module {
      trait SharedModule extends AppScalaModule with PlatformScalaModule with FoxxyPublish {
        override def ivyDeps = external.zio_js ++ external.tapir_js
      }

      object jvm extends SharedModule
      object js  extends SharedModule with AppScalaJSModule
    }

    object frontend extends AppScalaJSModule with FoxxyPublish {
      override def moduleKind: Target[ModuleKind] = ModuleKind.ESModule
      override def moduleDeps                     = Seq(shared.js)
      override def ivyDeps                        = external.zio ++ external.frontend
    }
  }

  object reference extends Module {
    object backend extends AppScalaModule {
      override def moduleDeps = Seq(
        foxxy.backend,
        foxxy.shared.jvm,
        foxxy.repo,
        foxxy.auth,
        reference.shared.jvm
      )

      object test extends ScalaTests {
        def moduleDeps    = Seq(backend)
        def ivyDeps       = Agg(
          ivy"dev.zio::zio-test:2.1.6",
          ivy"dev.zio::zio-test-sbt:2.1.6",
          ivy"dev.zio::zio-test-magnolia:2.1.6",
          ivy"com.softwaremill.sttp.tapir::tapir-sttp-client:1.10.15",
          ivy"org.testcontainers:testcontainers:1.20.0",
          ivy"org.testcontainers:postgresql:1.20.0",
          ivy"org.slf4j:slf4j-nop:2.0.16"
        )
        def testFramework = "zio.test.sbt.ZTestFramework"
      }
    }

    object shared extends Module {
      trait SharedModule extends AppScalaModule with PlatformScalaModule {
        override def ivyDeps    = external.zio ++ external.tapir_js
        override def moduleDeps = Seq(foxxy.shared.js)
      }

      object jvm extends SharedModule
      object js  extends SharedModule with AppScalaJSModule
    }

    object frontend extends AppScalaJSModule {
      override def moduleKind: Target[ModuleKind] = ModuleKind.ESModule
      override def moduleDeps                     = Seq(shared.js, foxxy.frontend)
      override def ivyDeps                        = external.frontend
    }

    object frontend_vite extends Module {
      def moduleDeps = Seq(frontend)

      def compile = T {
        val jsPath = frontend.fastLinkJS().dest.path

        if (!os.exists(frontend_vite.millSourcePath / "webapp")) {
          os.makeDir(frontend_vite.millSourcePath / "webapp")
        }
        os.copy(
          jsPath / "main.js",
          frontend_vite.millSourcePath / "webapp" / "main.js",
          replaceExisting = true
        )
        os.copy(
          jsPath / "main.js.map",
          frontend_vite.millSourcePath / "webapp" / "main.js.map",
          replaceExisting = true
        )
      }
    }
  }
}
