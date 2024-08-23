package foxxy.reference.frontend.pages

import be.doeraene.webcomponents.ui5.configkeys.{ButtonType, InputType}
import be.doeraene.webcomponents.ui5.{Button, Input, Toast}
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router
import foxxy.frontend.utils.*
import foxxy.reference.frontend.Page
import foxxy.reference.frontend.services.Storage
import foxxy.reference.shared.Endpoints
import foxxy.reference.shared.Endpoints.LoginRequest
import sttp.model.Uri
import zio.*

case class LoginPage(storage: Storage, router: Router[Page]) {
  def create = ZIO.attempt {
    val loginVar    = Var("")
    val passwordVar = Var("")
    val loginButton = new EventBus[Unit]
    val credentials = Signal.combine(loginVar.signal, passwordVar.signal)
    val loginStream = loginButton.stream.sample(credentials).flatMapSwitch { case (l, p) => loginRequest(l, p).toEventStream }

    val messages = loginStream.map {
      case Some(_) => "Login succeeded"
      case None    => "Login failed"
    }

    val logic = loginStream --> {
      case Some(_) => router.pushState(Page.Home)
      case None    => ()
    }

    hDiv(
      Toast(_.showFromTextEvents(messages)),
      justifyContent.center,
      alignItems.center,
      form(
        onSubmit.preventDefault.mapTo(()) --> loginButton,
        vDivA(
          width := "300px",
          Input(
            width := "100%",
            _.placeholder := "Login",
            onInput.mapToValue --> loginVar
          ),
          Input(
            width := "100%",
            _.placeholder := "Password",
            _.tpe         := InputType.Password,
            onInput.mapToValue --> passwordVar
          ),
          Button("Login", _.tpe := ButtonType.Submit)
        )
      ),
      //
      logic
    )
  }

  def loginRequest(login: String, password: String) =
    Endpoints.login
      .send(Uri("localhost:5004"))(LoginRequest(login, password))
      .someOrFail(Exception("Failed to login"))
      .tap(x => ZIO.attempt { storage.set("token", x) })
      .fold(_ => None, x => Some(x))

}
