package foxxy.reference.frontend.components

import com.raquo.laminar.api.L.*
import foxxy.frontend.utils.vDiv
import zio.*
import foxxy.frontend.utils.*
import foxxy.frontend.utils.given

val listCreator = zioChildAP { _ => (items: Signal[List[String]]) =>
  sealed trait State
  object State {
    case object Empty                extends State
    case class Editing(text: String) extends State
  }

  enum Event {
    case StartEditing
    case UpdateText(text: String)
    case Submit
  }

  val stateManager = StateManagerBuilder[State, Event](State.Empty) { (state, event) =>
    (state, event) match {
      case (State.Empty, Event.StartEditing)             => ZIO.succeed(State.Editing(""))
      case (State.Editing(text), Event.Submit)           => ZIO.succeed(State.Empty)
      case (State.Editing(_), Event.UpdateText(newText)) => ZIO.succeed(State.Editing(newText))
      case (s, _)                                        => ZIO.succeed(s)
    }
  }

  ZIO.attempt {
    vDiv(
      child <-- stateManager.signal.splitMatchOne
        .handleType[State.Empty.type] { (_, _) =>
          button("Add Item", onClick --> (_ => stateManager.updateSync(Event.StartEditing)))
        }
        .handleType[State.Editing] { (_, state) =>
          input(
            value <-- state.map(_.text),
            onInput.mapToValue --> { v =>
              stateManager.updateSync(Event.UpdateText(v))
            },
            onKeyPress.filter(_.key == "Enter") --> { _ =>
              stateManager.updateSync(Event.Submit)
            }
          )
        }
        .toSignal
    )
  }
}

val myApp = zioVDiv {
  ZIO.attempt {
    vDiv(
      listCreator(())(Signal.fromValue(List("Item A", "Item B", "Item C")))()
    )
  }
}
