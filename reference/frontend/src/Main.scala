package foxxy.reference.frontend

import be.doeraene.webcomponents.ui5.configkeys.IconName
import be.doeraene.webcomponents.ui5.{Button, ShellBar, SideNavigation}
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.*
import foxxy.frontend.utils.*
import foxxy.reference.frontend.components.myApp
import foxxy.reference.frontend.pages.{RoomPage, TodoListPage}
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
import foxxy.reference.frontend.pages.RoomsPage

sealed trait Page derives JsonCodec

object Page:
  case object Home          extends Page
  case object Login         extends Page
  case object Register      extends Page
  case object TodoList      extends Page
  case object Rooms         extends Page
  case class Room(id: UUID) extends Page

case class HomePage() {
  def render = ZIO.attempt {
    (for {
      fr <- Fiber.roots
      _  <- Console.printLine(s"Roots: ${fr.size}")
    } yield ()).repeat(Schedule.spaced(1.second)).toFutureUnsafe

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
    Route.static(Page.Register, root / "register" / endOfSegments),
    Route.static(Page.TodoList, root / "todos" / endOfSegments),
    Route.static(Page.Rooms, root / "rooms" / endOfSegments),
    Route[Page.Room, UUID](
      encode = page => page.id,
      decode = arg => Page.Room(arg),
      pattern = root / "room" / segment[UUID] / endOfSegments
    )
  )
)

case class Layout(authSerivce: AuthService) {
  def layout(content: HtmlElement) = ZIO.attempt {
    val toggleCollapseBus: EventBus[Unit] = new EventBus
    val collapsedSignal                   = toggleCollapseBus.events.scanLeft(false)((collapsed, _) => !collapsed)
    vDiv(
      ShellBar(
        _.primaryTitle      := "Foxxy TODO Reference App",
        _.showCoPilot       := true,
        _.slots.startButton := Button(
          _.icon := IconName.menu,
          _.events.onClick.mapTo(()) --> toggleCollapseBus.writer
        ),
        _.slots.profile     := div(
          authSerivce.isLoggedIn
        )
      ),
      hDiv(
        SideNavigation(
          _.collapsed <-- collapsedSignal,
          _.item(
            _.text := "Home",
            _.icon := IconName.home,
            router.navigateTo(Page.Home),
            _.selected <-- router.currentPageSignal.map(_ == Page.Home)
          ),
          _.item(
            _.text := "Todos",
            _.icon := IconName.home,
            router.navigateTo(Page.TodoList),
            _.selected <-- router.currentPageSignal.map(_ == Page.TodoList)
          ),
          _.item(
            _.text := "Rooms",
            _.icon := IconName.home,
            router.navigateTo(Page.Rooms),
            _.selected <-- router.currentPageSignal.map(_ == Page.Rooms)
          ),
          _.slots.fixedItems := SideNavigation.item(
            _.text := "Login",
            _.icon := IconName.`user-settings`,
            router.navigateTo(Page.Login),
            _.selected <-- router.currentPageSignal.map(_ == Page.Login)
          ),
          _.slots.fixedItems := SideNavigation.item(
            _.text := "Register",
            _.icon := IconName.`user-settings`,
            router.navigateTo(Page.Register),
            _.selected <-- router.currentPageSignal.map(_ == Page.Register)
          ),
          _.slots.fixedItems := SideNavigation.item(
            _.text := "Logout",
            _.icon := IconName.error,
            onClick --> { _ => authSerivce.logout },
            _.href := router.absoluteUrlForPage(Page.Home)
          )
        ),
        content
      )
    )
  }
}

import foxxy.frontend.utils.given

def renderPage(page: Page) =
  zioComponent {
    (page match {
      case Page.Home     =>
        ZIO.attempt {
          vDiv(
            "Home page",
            myApp()
          )
        }
      case Page.Login    => ZIO.serviceWithZIO[LoginPage] { _.create }
      case Page.Register => ZIO.serviceWithZIO[RegisterPage] { _.create }
      case Page.TodoList => ZIO.serviceWithZIO[TodoListPage] { _ => ZIO.succeed(vDiv(TodoListPage.page())) }
      case Page.Rooms    => ZIO.attempt { vDiv(RoomsPage.page()) }
      case Page.Room(id) => ZIO.attempt { vDiv(RoomPage.page(id)()) }
    }).flatMap(x => ZIO.serviceWithZIO[Layout] { _.layout(x) })
  }

object App extends ZIOAppDefault {
  override def run = makeFrontend(router, p => renderPage(p)())
    .provide(
      ZLayer.derive[LoginPage],
      ZLayer.derive[RegisterPage],
      ZLayer.derive[TodoListPage],
      ZLayer.derive[Storage],
      ZLayer.derive[MyHttpClient],
      ZLayer.succeed(router),
      ZLayer.derive[Layout],
      ZLayer.derive[AuthService],
      ZLayer.derive[MyWsClient]
    )
}
