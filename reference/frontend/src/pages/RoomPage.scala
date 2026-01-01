package foxxy.reference.frontend.pages
import be.doeraene.webcomponents.ui5.configkeys.BusyIndicatorSize
import be.doeraene.webcomponents.ui5.{BusyIndicator, Button, Input, UList}
import com.raquo.laminar.api.L.*
import foxxy.reference.frontend.services.MyWsClient
import foxxy.reference.shared.Domain.{PlaybackState, Room, RoomContent, RoomQueue, RoomUser, Video}
import foxxy.reference.shared.RoomEndpoints
import foxxy.reference.shared.RoomEndpoints.{ServerMessage, UserMessage}
import zio.*

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

object RoomPage {
  import foxxy.frontend.utils.*
  import foxxy.frontend.utils.given

  val page = zchildA: (roomId: UUID) =>
    for {
      httpClient <- ZIO.service[foxxy.reference.frontend.services.MyHttpClient]
      ws         <- ZIO.service[MyWsClient].map(_.ws(roomId))
    } yield {
      import httpClient.extensions.*

      enum State:
        case Loading
        case ReceivingData(userId: Option[UUID], room: Option[Room])
        case Viewing(userId: UUID, room: Room)

      enum Event:
        case UpdateUserId(userId: UUID)
        case UpdateRoom(room: Room)
        case OnVideoAdded(url: String)
        case OnVideoPlayed(videoId: UUID)
        case OnVideoRemoved(videoId: UUID)
        case OnPlaybackProgress(position: Int)
        case OnRefresh

      val state = StateManagerBuilder[State, Event](State.Loading) {
        case (State.Loading, Event.UpdateRoom(room))                             =>
          ZIO.succeed(State.ReceivingData(None, Some(room)))
        case (State.Loading, Event.UpdateUserId(userId))                         =>
          ZIO.succeed(State.ReceivingData(Some(userId), None))
        //
        case (State.ReceivingData(None, Some(room)), Event.UpdateUserId(userId)) =>
          ZIO.succeed(State.Viewing(userId, room))
        case (State.ReceivingData(Some(userId), None), Event.UpdateRoom(room))   =>
          ZIO.succeed(State.Viewing(userId, room))
        //
        case (State.Viewing(u, _), Event.UpdateRoom(room))                       =>
          ZIO.succeed(State.Viewing(u, room))
        case (s, Event.OnVideoAdded(url))                                        =>
          RoomEndpoints.addVideo.send((roomId, RoomEndpoints.AddVideoRequest(url))).as(s)
        case (s, Event.OnVideoPlayed(videoId))                                   =>
          RoomEndpoints.playVideo.send((roomId, videoId)).as(s)
        case (s, Event.OnVideoRemoved(videoId))                                  =>
          Console.printLine("Remove video not implemented").as(s)
        case (s, Event.OnRefresh)                                                =>
          ZIO.attempt { ws.sendOne(UserMessage.RequestUpdate()) }.as(s)
        //
        case (s, _)                                                              => Console.printLine("Unhandled event").as(s)
      }

      val wsHandler = (msg: ServerMessage) =>
        msg match
          case ServerMessage.UserId(userId) => state.updateAsync(Event.UpdateUserId(userId))
          case ServerMessage.Room(room)     => state.updateAsync(Event.UpdateRoom(room))
          case ServerMessage.Msg(msg)       => println(s"Received msg: $msg")

      vDiv(
        ws.connect,
        ws.received --> wsHandler,
        onMountCallback(_ => { state.updateAsync(Event.OnRefresh) }),
        hDivA(
          justifyContent.center,
          "Room Page"
        ),
        child <-- state.signal.splitMatchOne
          .handleType[State.Loading.type]: (_, _) =>
            BusyIndicator(
              _.delay  := FiniteDuration(0, "ms"),
              _.active := true,
              _.size   := BusyIndicatorSize.Large
            )
          .handleType[State.ReceivingData]: (_, _) =>
            BusyIndicator(
              _.delay  := FiniteDuration(0, "ms"),
              _.active := true,
              _.size   := BusyIndicatorSize.Large
            )
          .handleType[State.Viewing]: (_, s) =>
            vDiv(
              hDiv(
                vDiv(
                  flexGrow := 7,
                  child <--- roomContentComponent(s.map(_.room.content))(),
                  child <--- videoAddForm(x => state.updateAsync(Event.OnVideoAdded(x)))()
                ),
                vDiv(
                  flexGrow := 3,
                  child <--- userList(s.map(_.room.users))()
                )
              ),
              child <--- videoQueue(
                (onRemove = x => state.updateAsync(Event.OnVideoRemoved(x)), onPlay = x => state.updateAsync(Event.OnVideoPlayed(x))),
                s.map(_.room.videoQueue)
              )()
            )
          .toSignal
      )
    }

  val roomContentComponent = zchildP: (content: Signal[RoomContent]) =>
    ZIO.succeed:
      hDiv(
        justifyContent.center,
        child <--- content.splitMatchOne
          .handleType[RoomContent.Empty.type]: (_, _) =>
            emptyVideoPlayer
          .handleType[RoomContent.WithMovie]: (_, c) =>
            videoPlayer(c)
          .toSignal
      )

  val emptyVideoPlayer = zioChild:
    ZIO.succeed:
      videoTag(
        maxWidth.percent(100),
        maxHeight.percent(100),
        styleProp("aspect-ratio") := "16/9",
        backgroundColor           := "grey",
        onMountCallback(x => {
          x.thisNode.ref.controls = true
        })
      )

  val videoPlayer = zchildP: (content: Signal[RoomContent.WithMovie]) =>
    ZIO.succeed:
      videoTag(
        maxWidth.percent(100),
        maxHeight.percent(100),
        styleProp("aspect-ratio") := "16/9",
        backgroundColor           := "grey",
        src <-- content.map(c => c.video.source.video_url).distinct,
        onMountCallback(x => { x.thisNode.ref.controls = true })
      )

  val videoAddForm = zchildA: (onAdd: String => Unit) =>
    val url = Var("")
    hDivA(
      alignItems.center,
      Input(width.percent(100), value <-- url, onInput.mapToValue --> url.writer),
      hDivA(
        Button("Add video", onClick --> { _ => onAdd(url.now()) })
      )
    )

  val videoQueue = zchildAP: (args: (onRemove: UUID => Unit, onPlay: UUID => Unit), queue: Signal[RoomQueue]) =>
    UList(
      children <-- queue
        .map(_.videos)
        .split(_.id): (_, _, video) =>
          UList.item(
            child <--- videoQueueElement(
              (onRemove = args.onRemove, onPlay = args.onPlay),
              video
            )()
          )
    )

  val videoQueueElement = zchildAP: (args: (onRemove: UUID => Unit, onPlay: UUID => Unit), props: Signal[Video]) =>
    val (onRemove, onPlay) = args
    val video              = props
    hDivA(
      justifyContent.spaceBetween,
      child.text <-- video.map(_.source.video_url),
      hDivA(
        Button("Play", onClick.use(video) --> { v => onPlay(v.id) }),
        Button("Remove", onClick.use(video) --> { v => onRemove(v.id) })
      )
    )

  val userList = zchildP: (users: Signal[List[RoomUser]]) =>
    UList(
      children <-- users.split(_.id): (_, _, user) =>
        UList.item(
          hDiv(
            justifyContent.spaceBetween,
            hDiv(child.text <-- user.map(u => s"${u.name} (${u.id})")),
            hDivA(
              child.text <-- user.map(u =>
                s"${u.progress match
                    case PlaybackState.Playing(time) => s"Playing: ${time.toSeconds} seconds"
                    case PlaybackState.Paused(time)  => s"Paused: ${time.toSeconds} seconds"
                    case PlaybackState.Idle          => "Idle"
                  }"
              )
            )
          )
        )
    )
}
