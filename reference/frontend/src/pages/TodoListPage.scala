package foxxy.reference.frontend.pages

import be.doeraene.webcomponents.ui5.Button
import be.doeraene.webcomponents.ui5.UList
import com.raquo.laminar.api.L.{*}
import foxxy.frontend.utils.*
import foxxy.reference.frontend.services.*
import foxxy.reference.shared.Endpoints
import foxxy.reference.shared.Endpoints.AddTodoRequest
import foxxy.reference.shared.Endpoints.TodoResponse
import foxxy.reference.shared.Endpoints.UpdateTodoRequest
import zio.*

import java.util.UUID
import com.raquo.waypoint.Router
import foxxy.reference.frontend.Page
import be.doeraene.webcomponents.ui5.Input

case class TodoListPage(storage: Storage, httpClient: MyHttpClient, router: Router[Page]) {
  import httpClient.extensions._

  val getTodos   = Endpoints.getTodos.sendSecure(())
  val addTodo    = (name: String) => Endpoints.addTodo.sendSecure(AddTodoRequest(name))
  val updateTodo = (id: UUID, completed: Boolean) => Endpoints.updateTodo.sendSecure(id, UpdateTodoRequest(completed))
  val removeTodo = (id: UUID) => Endpoints.removeTodo.sendSecure(id)

  def create = ZIO.attempt {
    val text            = Var("")
    val update          = EventBus[Unit]()
    val addAndUpdate    = (x: String) => addTodo(x).tap(_ => ZIO.attempt(update.emit(())))
    val deleteAndUpdate = (x: UUID) => removeTodo(x).tap(_ => ZIO.attempt(update.emit(())))
    val displayTodos    = update.events.flatMapSwitch(_ => getTodos.toEventStream)

    def renderItem(todo: TodoResponse) = {
      hDivA(
        maxWidth.em(40),
        justifyContent.spaceBetween,
        hDivA(
          alignItems.center,
          gap.em(0.5),
          // CheckBox(_.checked := todo.completed, 0.mapToValue --> {x => updateAndUpdate(todo.id, x).toFutureUnsafe}),
          p(todo.text)
        ),
        Button("Remove", onClick.mapToUnit --> { _ => deleteAndUpdate(todo.id).toFutureUnsafe })
      )
    }

    vDiv(
      alignItems.center,
      vDivA(
        width.em(40),
        Input(onChange.mapToValue --> text, width.percent(100)),
        Button("Add todo", onClick.mapTo(text.now()) --> { x => addAndUpdate(x).toFutureUnsafe }),
        UList(children <-- displayTodos.collectRight.map(_.map { x => renderItem(x) })),
        onMountCallback(_ => update.emit(()))
      )
    )
  }
}
