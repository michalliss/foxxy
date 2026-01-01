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
import foxxy.frontend.utils.*
import foxxy.frontend.utils.given
import java.util.UUID
import be.doeraene.webcomponents.ui5.Form
import be.doeraene.webcomponents.ui5.configkeys.ButtonType
import be.doeraene.webcomponents.ui5.configkeys.IconName

object RoomsPage2 {
  val page = zchild:
    for {
      httpClient <- ZIO.service[MyHttpClient]
      router     <- ZIO.service[Router[Page]]
    } yield {
      import httpClient.extensions._

      case class State(rooms: List[Room])
      enum Event:
        case RefreshStart
        case RefreshComplete(rooms: List[Room])
        case SelectRoom(id: UUID)
        case DeleteRoom(id: UUID)

      val state = stateManager[State](State(Nil))[Event] {
        case (_, Event.RefreshStart)               =>
          Eff(
            RoomEndpoints.getRooms
              .send(())
              .some
              .orElseFail(Throwable("Failed to fetch rooms 2"))
              .map { rooms => Some(Event.RefreshComplete(rooms)) }
          )
        case (_, Event.DeleteRoom(id))             =>
          Eff(
            RoomEndpoints.deleteRoom
              .send(id)
              .some
              .orElseFail(Throwable("Failed to delete room"))
              .map(_ => Some(Event.RefreshStart))
          )
        case (state, Event.RefreshComplete(rooms)) => Pure(state.copy(rooms = rooms))
        case (state, Event.SelectRoom(id))         => Pure { router.pushState(Page.Room(id)); state }
      }

      vDiv(
        padding.em(1),
        onMountCallback(_ => state.update(Event.RefreshStart)),
        child <--- RoomAddComponent.component(_ => state.update(Event.RefreshStart)),
        div(paddingTop := "16px"),
        child <--- roomListComponent(
          (
            onRoomClick = id => state.update(Event.SelectRoom(id)),
            onRoomDelete = id => state.update(Event.DeleteRoom(id))
          ),
          state.signal.map(_.rooms)
        )()
      )
    }

  val roomListComponent = zchildAP: (args: (onRoomClick: UUID => Unit, onRoomDelete: UUID => Unit), rooms: Signal[List[Room]]) =>
    println("Rendering room list component")
    vDiv(
      UList(
        children <--- rooms.ssplit(_.id): room =>
          println("ssplit")
          itemComponent(
            (onClick = (id: UUID) => args.onRoomClick(id), onDelete = (id: UUID) => args.onRoomDelete(id)),
            room.distinct
          )
      )
    )

  val itemComponent = zchildAP: (args: (onClick: UUID => Unit, onDelete: UUID => Unit), room: Signal[Room]) =>
    println("rendering Item")
    UList.item(
      width := "100%",
      hDiv(
        justifyContent.spaceBetween,
        alignItems.center,
        hDiv(
          onClick.use(room) --> { r => args.onClick(r.id) },
          span(child.text <-- room.map(_.name))
        ),
        hDivA(
          alignItems.center,
          gap.em(1),
          span(child.text <-- room.map(r => r.users.size.toString + " users")),
          hDivA(
            be.doeraene.webcomponents.ui5.Button(
              _.icon := IconName.delete,
              onClick.use(room) --> { r => args.onDelete(r.id) }
            )
          )
        )
      )
    )
}

object RoomAddComponent {
  val component = zchildA: (onRoomAdded: Unit => Unit) =>
    for {
      httpClient <- ZIO.service[MyHttpClient]
    } yield {
      import httpClient.extensions._

      case class State(input: String)
      enum Event:
        case UpdateInput(text: String)
        case Submit

      val state = stateManager(State(""))[Event] {
        case (state, Event.UpdateInput(text)) => Pure(state.copy(input = text))
        case (state, Event.Submit)            =>
          Eff(
            RoomEndpoints.createRoom
              .send(RoomEndpoints.CreateRoomRequest(state.input))
              .some
              .orElseFail(Throwable("Failed to create room"))
              .tap(_ => ZIO.attempt { onRoomAdded(()) })
              .map(_ => Some(Event.UpdateInput(""))) // Clear input after successful submission
          )
      }

      vDivA(
        Form(
          _.headerText := "Create a New Room",
          form(
            hDivA(
              alignItems.center,
              be.doeraene.webcomponents.ui5.Input(
                width.percent(100),
                placeholder := "New room name",
                value <-- state.signal.map(_.input),
                onInput.mapToValue --> { v => state.update(Event.UpdateInput(v)) }
              ),
              hDivA(
                be.doeraene.webcomponents.ui5
                  .Button("Create Room", _.tpe := ButtonType.Submit)
              )
            ),
            onSubmit.preventDefault.use(state.signal) --> { _ =>
              state.update(Event.Submit)
            }
          )
        )
      )

    }
}
