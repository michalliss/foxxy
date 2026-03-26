package foxxy.reference.frontend

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.*
import foxxy.frontend.utils.*
import foxxy.frontend_elements.Divs.*
import foxxy.frontend_zio.ZioLaminar.*
import urldsl.errors.ErrorFromThrowable
import urldsl.vocabulary.{FromString, Printer}
import zio.*
import zio.json.JsonCodec
import zio.stream.ZStream

import java.util.UUID
import scala.util.{Failure, Success, Try}

import pages.LoginPage
import pages.RegisterPage
import services.*

sealed trait Page derives JsonCodec

object Page:
  case object Home     extends Page
  case object Login    extends Page
  case object Register extends Page

case class HomePage() {
  def render = ZIO.attempt {
    div(
      p("Home page"),
      p(
        text <-- ZStream
          .repeatZIOWithSchedule(Random.nextInt, Schedule.spaced(1.second))
          .map(_.toString)
          .toEventStream
      )
    )
  }
}

given [T](using fromThrowable: ErrorFromThrowable[T]): FromString[UUID, T] =
  FromString.factory(str =>
    Try(UUID.fromString(str)) match {
      case Success(value)     => Right(value)
      case Failure(exception) => Left(fromThrowable.fromThrowable(exception))
    }
  )

given Printer[UUID] = Printer.factory(_.toString)

val router = makeRouter[Page](
  List(
    Route.static(Page.Home, root / endOfSegments),
    Route.static(Page.Login, root / "login" / endOfSegments),
    Route.static(Page.Register, root / "register" / endOfSegments)
  )
)

case class Layout(authSerivce: AuthService) {
  def layout(content: HtmlElement) = ZIO.attempt {
    val toggleCollapseBus: EventBus[Unit] = new EventBus
    toggleCollapseBus.events.scanLeft(false)((collapsed, _) => !collapsed)
    vDiv(
      content
    )
  }
}


import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
@JSImport("@awesome.me/webawesome/dist/styles/webawesome.css", JSImport.Namespace)
@js.native
object Stylesheet extends js.Object
val _ = Stylesheet

def renderPage(page: Page) =
  zioComponent {
    (page match {
      case Page.Home     =>
        ZIO.attempt {
          vDiv(
            "Home page"
          )
        }
      case Page.Login    => ZIO.serviceWithZIO[LoginPage] { _.create }
      case Page.Register => ZIO.serviceWithZIO[RegisterPage] { _.create }
    }).flatMap(x => ZIO.serviceWithZIO[Layout] { _.layout(x) })
  }

object App extends ZIOAppDefault {
  override def run = makeFrontend(router, p => renderPage(p)())
    .provide(
      ZLayer.derive[LoginPage],
      ZLayer.derive[RegisterPage],
      ZLayer.derive[Storage],
      ZLayer.succeed(router),
      ZLayer.derive[Layout],
      ZLayer.derive[AuthService],
      ZLayer.derive[MyWsClient]
    )
}
