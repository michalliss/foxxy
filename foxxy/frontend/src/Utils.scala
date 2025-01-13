package foxxy.frontend.utils

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.raquo.waypoint.*
import org.scalajs.dom
import org.scalajs.dom.HTMLDivElement
import sttp.client3.*
import sttp.client3.impl.zio.FetchZioBackend
import sttp.model.Uri
import sttp.tapir.DecodeResult.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.{Endpoint, PublicEndpoint}
import zio.*
import zio.json.*
import zio.stream.*
import io.laminext.websocket.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

extension [E, A](effect: ZIO[Any, E, A])
  def toFutureUnsafe =
    Unsafe.unsafe(implicit u => zio.Runtime.default.unsafe.runToFuture(effect.mapError(x => new RuntimeException(x.toString))))

  def runUnsafe =
    Unsafe.unsafe(implicit u => zio.Runtime.default.unsafe.run(effect.mapError(x => new RuntimeException(x.toString))))

  def toEventStream =
    EventStream.fromFuture(effect.toFutureUnsafe)

extension [E](effect: ZIO[Any, E, HtmlElement])
  def asChild                            = child <-- EventStream.fromFuture(effect.toFutureUnsafe)
  def asChildWithLoading(x: HtmlElement) = child <-- EventStream.fromFuture(effect.toFutureUnsafe).toSignal(x)

extension [E, A](stream: ZStream[Any, E, A])
  def toEventStream = {
    val endStreamPromise = scala.concurrent.Promise[Unit]()
    EventStream.fromCustomSource[A](
      start = (fireEvent, fireError, _, _) => {
        stream
          .interruptWhen(ZIO.fromPromiseScala(endStreamPromise))
          .runForeachChunk(x => ZIO.succeed(x.foreach(elem => fireEvent(elem))))
          .tapError(x => ZIO.succeed(fireError(Throwable(x.toString()))))
          .toFutureUnsafe
      },
      stop = _ => {
        println("stopped")
        endStreamPromise.trySuccess(())
      }
    )
  }

def getRequest[A: JsonDecoder](url: String) = {
  ZIO.fromFuture(context =>
    basicRequest //
      .get(uri"$url")
      .send(FetchBackend())
      .map(_.body.toOption.map(x => x.fromJson[A].toOption))
      .map(_.flatten)(context)
  )
}

enum SendError:
  case DecodeError
  case RequestError[TErr](err: TErr)
  case UnknownError(err: Throwable)

extension [T1, T2, T3, T4](endpoint: Endpoint[T1, T2, T3, T4, Any])
  def sendSecure(uri: Uri) = {
    SttpClientInterpreter()
      .toSecureClient(endpoint, baseUri = Some(uri), backend = FetchZioBackend())
      .andThen(x =>
        x.andThen(x =>
          x
            .map(_ match
              case Value(v) => Some(v)
              case _        => None)
            .mapError(x => SendError.UnknownError(x))
            .someOrFail(SendError.DecodeError)
            .map(x => x)
        )
      )
  }

extension [T1, T2, T3](endpoint: PublicEndpoint[T1, T2, T3, Any])
  def send(uri: Uri) = {
    SttpClientInterpreter()
      .toClient(endpoint, baseUri = Some(uri), backend = FetchZioBackend())
      .andThen(x =>
        x
          .map(_ match
            case Value(v) => Some(v)
            case _        => None)
          .map(_.map(x => x.toOption))
          .map(_.flatten)
      )
  }

def vDiv(inputMods: Modifier[Div]*) = div(
  overflow.auto,
  alignItems.stretch,
  height    := "100%",
  width     := "100%",
  display.flex,
  flexDirection.column,
  boxSizing.borderBox,
  flexBasis := "0",
  flexGrow  := 1,
  inputMods
)

def hDiv(inputMods: Modifier[Div]*) = div(
  overflow.auto,
  alignItems.stretch,
  width     := "100%",
  height    := "100%",
  display.flex,
  flexDirection.row,
  boxSizing.borderBox,
  flexBasis := "0",
  flexGrow  := 1,
  inputMods
)

def hDivA(inputMods: Modifier[Div]*) = hDiv(
  overflow.unset,
  width.auto,
  height.auto,
  flexBasis.unset,
  flexGrow.unset,
  inputMods
)

def vDivA(inputMods: Modifier[Div]*) = vDiv(
  overflow.unset,
  width.auto,
  height.auto,
  flexBasis.unset,
  flexGrow.unset,
  inputMods
)

extension [T](router: Router[T])
  def navigateTo(page: T): Binder[HtmlElement] = Binder { el =>
    val isLinkElement = el.ref.isInstanceOf[dom.html.Anchor]
    if (isLinkElement) {
      Try(router.absoluteUrlForPage(page)) match {
        case Success(url) => el.amend(href(url))
        case Failure(err) => dom.console.error(err)
      }
    }
    (onClick
      .filter(ev => !(isLinkElement && (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey)))
      .preventDefault
      --> (_ => router.pushState(page))).bind(el)
  }

def makeRouter[T: JsonCodec](routes: List[Route[? <: T, ?]]): Router[T] =
  Router[T](
    routes = routes,
    getPageTitle = _.toString,
    serializePage = page => page.toJson,
    deserializePage = pageStr => pageStr.fromJson[T].toOption.get
  )

def makeRouter[T: JsonCodec](routes: Route[? <: T, ?]*): Router[T] = makeRouter(routes.toList)

def makeFrontend[T, R](router: Router[T], renderFn: T => ZIO[R, Throwable, HtmlElement]) = {
  for {
    env <- ZIO.environment[R]
    app <- ZIO.attempt {
             hDiv(
               child <-- router.currentPageSignal.map(page =>
                 Unsafe.unsafely {
                   zio.Runtime.default
                     .withEnvironment(env)
                     .unsafe
                     .run(renderFn(page))
                     .getOrThrow()
                 }
               )
             )
           }
    _   <- ZIO.attempt { render(dom.document.querySelector("#root"), app) }
  } yield ()
}

def makeWs[ServerMessage: JsonCodec, ClientMessage: JsonCodec](url: String) = {
  WebSocket
    .url(url)
    .receiveText(_.fromJson[ServerMessage].left.map(x => Throwable(x.toString)))
    .sendText((x: ClientMessage) => x.toJson)
    .build(managed = true)
}

class ZioObserver[A](fn: A => ZIO[Any, Throwable, Unit]) extends Observer[A] {

  override def onNext(nextValue: A): Unit = { fn(nextValue).toFutureUnsafe }

  override def onError(err: Throwable): Unit = ()

  override def onTry(nextValue: Try[A]): Unit = nextValue.map(onNext)

}
