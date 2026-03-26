package foxxy.frontend.utils

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.*
import io.laminext.websocket.*
import org.scalajs.dom
import zio.*
import zio.json.*
import zio.schema.*
implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

import scala.util.{Failure, Success, Try}
import com.raquo.airstream.split.DuplicateKeysConfig
import com.raquo.airstream.split.Splittable
import foxxy.frontend_elements.Divs.*

extension [M[_], In](signal: Signal[M[In]]) {
  def ssplit[Output, Key](
      key: In => Key,
      distinctCompose: (Signal[In] => Signal[In]) = (x: Signal[In]) => x.distinct,
      duplicateKeys: DuplicateKeysConfig = DuplicateKeysConfig.default
  )(
      project: (Signal[In]) => Output
  )(implicit splittable: Splittable[M]): Signal[M[Output]] = signal.split(
    key,
    distinctCompose,
    duplicateKeys
  )((_, _, s) => project(s))
}

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

def makeFrontend[T, R](router: Router[T], renderFn: T => zio.Runtime[R] ?=> ZIO[R, Throwable, HtmlElement]) = {
  for {
    env                  <- ZIO.environment[R]
    given zio.Runtime[R] <- ZIO.runtime[R]
    app                  <- ZIO.attempt {
                              vDiv(
                                child <-- router.currentPageSignal.flatMapSwitch((page: T) =>
                                  Signal.fromFuture(
                                    Unsafe.unsafely {
                                      zio.Runtime.default
                                        .withEnvironment(env)
                                        .unsafe
                                        .runToFuture(renderFn(page))
                                    },
                                    div()
                                  )
                                )
                              )
                            }
    _                    <- ZIO.attempt { render(dom.document.querySelector("#root"), app) }
  } yield ()
}

def makeWs[ServerMessage: Schema, ClientMessage: Schema](url: String) = {
  given a: zio.json.JsonCodec[ServerMessage] = zio.schema.codec.JsonCodec.jsonCodec(summon[Schema[ServerMessage]])
  given b: zio.json.JsonCodec[ClientMessage] = zio.schema.codec.JsonCodec.jsonCodec(summon[Schema[ClientMessage]])
  WebSocket
    .url(url)
    .receiveText(_.fromJson[ServerMessage].left.map(x => Throwable(x.toString)))
    .sendText((x: ClientMessage) => x.toJson)
    .build(managed = true)
}
