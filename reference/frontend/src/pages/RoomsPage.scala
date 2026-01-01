package foxxy.reference.frontend.pages

import be.doeraene.webcomponents.ui5.UList
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router
import foxxy.frontend.utils.ReducerResult.{Pure, Eff}
import foxxy.reference.frontend.Page
import foxxy.reference.frontend.services.MyHttpClient
import foxxy.reference.shared.Domain.Room
import foxxy.reference.shared.RoomEndpoints
import zio.*
import zio.stream.ZStream

import java.util.UUID

object RoomsPage {
  import foxxy.frontend.utils.*
  import foxxy.frontend.utils.given

  val page = zioChild {
    for {
      httpClient <- ZIO.service[MyHttpClient]
      router     <- ZIO.service[Router[Page]]
    } yield {
      import httpClient.extensions._
      case class State(rooms: List[Room])

      enum Event:
        case Refresh
        case SelectRoom(id: UUID)

      val state = StateManagerBuilder[State, Event](State(Nil)) {
        case (State(rooms), Event.Refresh) =>
          for {
            rooms <- RoomEndpoints.getRooms.send(()).some.orElseFail(Throwable("Failed to fetch rooms 1"))
            _     <- Console.printLine(s"Fetched rooms: $rooms")
          } yield State(rooms)
        case (state, Event.SelectRoom(id)) =>
          ZIO.attempt:
            println(s"Selected room: $id")
            router.pushState(Page.Room(id))
            state
      }

      vDiv(
        onMountCallback(_ => state.updateAsync(Event.Refresh)),
        child <--- showcaseComponent,
        child <--- roomListComponent((onRoomClick = id => state.updateAsync(Event.SelectRoom(id))), state.signal.map(_.rooms))(),
      )
    }
  }

  val roomListComponent = zchildAP: (args: (onRoomClick: UUID => Unit), rooms: Signal[List[Room]]) =>
    vDiv(
      text <-- rooms.map(_.size),
      UList(
        children <--- rooms.split(_.id): (_, _, room) =>
          itemComponent(
            (onClick = (id: UUID) => args.onRoomClick(id)),
            room
          ),
        children <--- rooms.ssplit(_.id): room =>
          itemComponent(
            (onClick = (id: UUID) => args.onRoomClick(id)),
            room
          )
      )
    )

  val itemComponent = zchildAP: (args: (onClick: UUID => Unit), room: Signal[Room]) =>
    UList.item(
      width := "100%",
      "Room: ",
      child.text <-- room.map(_.name),
      onClick.use(room) --> { r => args.onClick(r.id) }
    )

  val helloWorldComponent = zchildA: (args: String) =>
    div("Hello World: " + args)

  val helloWorldDynamic = zchildAP: (args: String, props: Signal[String]) =>
    div(
      "Hello World Dynamic. Starting arg: " + args,
      ", prop changes: ",
      child.text <-- props
    )

  val helloWorldZIO = zchild:
    for {
      time <- Clock.instant
    } yield {
      div("Hello World ZIO at time: " + time.toString)
    }

  val helloWorldZIOStream = zchild:
    for {
      counter <- Ref.make(0)
      stream   = ZStream.tick(100.millis).mapZIO(_ => counter.updateAndGet(_ + 1))
    } yield div(
      "Hello World ZIO Stream counter: ",
      child.text <-- stream.toEventStream
    )

  val statefulComponent = zchild:
    case class State(counter: Int)
    enum Event:
      case StartIncrementing
      case FinishIncrementing

    val state = stateManager(State(0))[Event] {
      case (_, Event.StartIncrementing)      =>
        Eff(ZIO.some(Event.FinishIncrementing).delay(2.seconds))
      case (state, Event.FinishIncrementing) =>
        Pure(state.copy(counter = state.counter + 1))
    }

    hDiv(
      "Stateful counter: ",
      child.text <-- state.signal.map(_.counter),
      be.doeraene.webcomponents.ui5.Button("Increment", onClick --> { _ => state.update(Event.StartIncrementing) })
    )

  val showcaseComponent = zchild:
    vDiv(
      child <--- helloWorldZIO,
      child <--- helloWorldZIOStream,
      child <--- helloWorldZIOStream,
      child <--- helloWorldZIOStream,
      child <--- helloWorldZIOStream,
      child <--- helloWorldZIOStream,
      child <--- helloWorldComponent("Static Hello World"),
      child <--- helloWorldDynamic(
        "Initial Prop",
        EventStream
          .periodic(1000)
          .map(tick => s"Prop update #$tick")
          .toSignal("No updates yet")
      ),
      child <--- statefulComponent
    )
}
