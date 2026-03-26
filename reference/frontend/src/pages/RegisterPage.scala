package foxxy.reference.frontend.pages

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router
import foxxy.reference.frontend.Page
import foxxy.reference.frontend.services.Storage
import zio.*

case class RegisterPage(storage: Storage, router: Router[Page]) {
  def create = ZIO.attempt {
    div("Register")
    // val loginVar       = Var("")
    // val passwordVar    = Var("")
    // val registerButton = new EventBus[Unit]
    // val credentials    = Signal.combine(loginVar.signal, passwordVar.signal)
    // val registerStream = registerButton.stream.sample(credentials).flatMapSwitch { case (l, p) => registerRequest(l, p).toEventStream }

    // val messages = registerStream.map {
    //   case Some(_) => "Registration succeeded"
    //   case None    => "Registration failed"
    // }

    // val logic = registerStream.delay(1000) --> {
    //   case Some(_) => router.pushState(Page.Login)
    //   case None    => ()
    // }

    // hDiv(
    //   Toast(_.showFromTextEvents(messages)),
    //   justifyContent.center,
    //   alignItems.center,
    //   form(
    //     onSubmit.preventDefault.mapTo(()) --> registerButton,
    //     vDivA(
    //       width := "300px",
    //       Input(
    //         width := "100%",
    //         _.placeholder := "Login",
    //         onInput.mapToValue --> loginVar
    //       ),
    //       Input(
    //         width := "100%",
    //         _.placeholder := "Password",
    //         _.tpe         := InputType.Password,
    //         onInput.mapToValue --> passwordVar
    //       ),
    //       Button("Register", _.tpe := ButtonType.Submit)
    //     )
    //   ),
    //   //
    //   logic
    // )
  }

  def registerRequest(login: String, password: String): ZIO[Any, Nothing, Option[String]] = ???

}
