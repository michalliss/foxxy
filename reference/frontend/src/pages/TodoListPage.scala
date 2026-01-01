package foxxy.reference.frontend.pages

import be.doeraene.webcomponents.ui5.{Button, Input, UList}
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router
import foxxy.frontend.utils.*
import foxxy.reference.frontend.Page
import foxxy.reference.frontend.services.*
import foxxy.reference.shared.Endpoints
import foxxy.reference.shared.Endpoints.{AddTodoRequest, TodoResponse, UpdateTodoRequest}
import zio.*

import java.util.UUID

object TodoListPage:
  import foxxy.frontend.utils.given
  import foxxy.frontend.utils.*

  val page = zioChild {
    for {
      httpClient <- ZIO.service[MyHttpClient]
    } yield {
      import httpClient.extensions._

      enum State:
        case Viewing(todos: List[TodoResponse], filter: String)

      enum Event:
        case AddItem(name: String)
        case RemoveItem(id: UUID)
        case ApplyFilter(text: String)
        case ClearFilter
        case Refresh

      val stateManager = StateManagerBuilder[State, Event](State.Viewing(Nil, "")) { (state, event) =>
        (state, event) match {
          case (State.Viewing(todos, _), Event.ApplyFilter(t))      => ZIO.succeed(State.Viewing(todos, t))
          case (State.Viewing(todos, _), Event.ClearFilter)         => ZIO.succeed(State.Viewing(todos, ""))
          case (State.Viewing(todos, filter), Event.AddItem(n))     =>
            ZIO.suspend {
              for {
                _     <- Endpoints.addTodo.sendSecure(AddTodoRequest(n)).right.orElseFail(Throwable("Failed to add todo"))
                todos <- Endpoints.getTodos.sendSecure(()).right.orElseFail(Throwable("Failed to fetch todos"))
              } yield State.Viewing(todos, filter)
            }
          case (State.Viewing(todos, filter), Event.RemoveItem(id)) =>
            ZIO.suspend {
              for {
                _     <- Endpoints.removeTodo
                           .sendSecure(id)
                           .right
                           .orElseFail(
                             Throwable(
                               "Failed to remove todo"
                             )
                           )
                todos <- Endpoints.getTodos
                           .sendSecure(())
                           .right
                           .orElseFail(
                             Throwable(
                               "Failed to fetch todos"
                             )
                           )
              } yield State.Viewing(todos, filter)
            }
          case (_, Event.Refresh)                                   =>
            ZIO.suspend {
              for {
                todos <- Endpoints.getTodos.sendSecure(()).right.orElseFail(Throwable("Failed to fetch todos"))
              } yield State.Viewing(todos, "")
            }
        }
      }

      vDivA(
        onMountCallback(_ => stateManager.updateAsync(Event.Refresh)),
        searchControlsComponent(x => stateManager.updateSync(Event.ApplyFilter(x)))(Signal.fromValue(()))(),
        addComponent(name => stateManager.updateAsync(Event.AddItem(name)))(Signal.fromValue(()))(),
        listComponent(id => stateManager.updateAsync(Event.RemoveItem(id)))(stateManager.signal.map { case State.Viewing(todos, filter) =>
          todos.filter(_.text.contains(filter))
        })()
      )
    }
  }

  val itemComponent = zioChildAP { (onRemove: UUID => Unit) => (item: Signal[TodoResponse]) =>
    ZIO.attempt {
      println("Rendering item component")
      hDiv(
        justifyContent.spaceBetween,
        alignItems.center,
        hDiv(
          alignItems.center,
          gap.em(0.5),
          child <-- item.map(i => p(i.text))
        ),
        Button("Remove", onClick.compose(_.withCurrentValueOf(item).map { case (_, i) => i }) --> { i => onRemove(i.id) })
      )
    }
  }

  val listComponent = zioChildAP { (onRemove: UUID => Unit) => (items: Signal[List[TodoResponse]]) =>
    println("Rendering list component")
    ZIO.succeed(
      UList(
        children <-- items.signal.split(_.id) { (_, _, item) =>
          li(
            itemComponent(id => onRemove(id))(item)()
          )
        }
      )
    )
  }

  val addComponent = zioChildAP { (onSubmit: (String => Unit)) => _ =>
    case class State(text: String)

    enum Event:
      case UpdateText(text: String)
      case Submit

    val stateManager = StateManagerBuilder[State, Event](State("")) {
      case (State(text), Event.UpdateText(newText)) => ZIO.succeed(State(newText))
      case (State(text), Event.Submit)              => ZIO.succeed { onSubmit(text); State("") }
    }

    ZIO.attempt {
      hDivA(
        alignItems.center,
        hDiv(
          Input(
            placeholder := "New todo",
            value <-- stateManager.signal.map(_.text),
            onInput.mapToValue.map(Event.UpdateText.apply) --> stateManager.updateSync,
            onKeyPress.filter(_.key == "Enter") --> (_ => stateManager.updateSync(Event.Submit)),
            width.percent(100)
          )
        ),
        Button("Add todo", onClick.mapTo(Event.Submit) --> stateManager.updateSync)
      )
    }
  }

  val searchControlsComponent = zioChildAP { (onSearch: (String => Unit)) => _ =>
    enum State:
      case Searching(text: String)

    enum Event:
      case UpdateText(text: String)
      case Submit
      case Clear

    val stateManager = StateManagerBuilder[State, Event](State.Searching("")) { (state, event) =>
      (state, event) match {
        case (State.Searching(_), Event.UpdateText(t)) => ZIO.succeed(State.Searching(t))
        case (State.Searching(t), Event.Submit)        =>
          ZIO.succeed {
            onSearch(t)
            State.Searching(t)
          }
        case (State.Searching(_), Event.Clear)         =>
          ZIO.succeed {
            onSearch("")
            State.Searching("")
          }
      }
    }

    ZIO.attempt {
      hDivA(
        child <-- stateManager.signal.splitMatchOne
          .handleType[State.Searching] { (_, state) =>
            hDiv(
              gap.em(0.5),
              hDiv(
                Input(
                  placeholder := "Search",
                  value <-- state.map(_.text),
                  onInput.mapToValue.map(Event.UpdateText.apply) --> stateManager.updateSync,
                  onKeyPress.filter(_.key == "Enter") --> (_ => stateManager.updateSync(Event.Submit)),
                  width.percent(100)
                )
              ),
              Button("Clear", onClick.mapTo(Event.Clear) --> stateManager.updateSync)
            )
          }
          .toSignal
      )
    }
  }

case class TodoListPage(httpClient: MyHttpClient, authService: AuthService, router: Router[Page]) {
  import httpClient.extensions._

  sealed trait Command;
  object Command {
    case class Add(name: String)    extends Command
    case class Delete(id: UUID)     extends Command
    case class Filter(text: String) extends Command
  }

  def create = ZIO.attempt {

    val items  = Var(List.empty[TodoResponse])
    val filter = Var("")

    def fetchItems = Endpoints.getTodos.sendSecure(()).right.tap(x => ZIO.attempt(items.set(x)))

    val commandObserver = Observer[Command] {
      case Command.Add(name)    => (Endpoints.addTodo.sendSecure(AddTodoRequest(name)) *> fetchItems).toFutureUnsafe
      case Command.Delete(id)   => (Endpoints.removeTodo.sendSecure(id) *> fetchItems).toFutureUnsafe
      case Command.Filter(text) => filter.set(text)
    }

    val filteredItems = items.signal.combineWith(filter.signal).map { case (items, filter) =>
      items.filter(_.text.contains(filter))
    }

    vDiv(
      alignItems.center,
      vDivA(
        width.em(40),
        SearchComponent(commandObserver),
        AddComponent(commandObserver),
        child <-- filteredItems.signal.map(items => ListComponent(items, onRemove = commandObserver)),
        onMountCallback(_ => fetchItems.toEventStream)
      )
    )
  }

  def SearchComponent(onSearch: Observer[Command.Filter]) = {
    Input(
      width.percent(100),
      placeholder := "Search",
      onInput.mapToValue.map(Command.Filter.apply) --> onSearch
    )
  }

  def AddComponent(onSubmit: Observer[Command.Add]) = {
    val text = Var("")
    vDivA(
      Input(onChange.mapToValue --> text, width.percent(100)),
      Button("Add todo", onClick.mapTo(Command.Add(text.now())) --> onSubmit)
    )
  }

  def ItemComponent(item: TodoResponse, onRemove: Observer[Unit]) = {
    hDivA(
      maxWidth.em(40),
      justifyContent.spaceBetween,
      hDivA(
        alignItems.center,
        gap.em(0.5),
        // CheckBox(_.checked := todo.completed, 0.mapToValue --> {x => updateAndUpdate(todo.id, x).toFutureUnsafe}),
        p(item.text)
      ),
      Button("Remove", onClick.mapToUnit --> onRemove)
    )
  }

  def ListComponent(items: List[TodoResponse], onRemove: Observer[Command.Delete]) = {
    UList(
      items.map { item =>
        ItemComponent(item, onRemove.contramap(_ => Command.Delete(item.id)))
      }
    )
  }

  val updateTodo = (id: UUID, completed: Boolean) => Endpoints.updateTodo.sendSecure(id, UpdateTodoRequest(completed))
}
