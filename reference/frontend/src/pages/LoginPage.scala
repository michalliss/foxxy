package foxxy.reference.frontend.pages
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router
import foxxy.frontend_zio.ZioLaminar.*
import foxxy.reference.frontend.Page
import foxxy.reference.frontend.services.Storage
import io.github.nguyenyou.webawesome.laminar.*
import zio.*

case class LoginPage(storage: Storage, router: Router[Page]) {

  def element = zchild {
    ZIO.attempt {
      div("xD")
    }
  }

  def create = ZIO.attempt {
    Button()("WebAwesome")
  }

  def loginRequest(login: String, password: String): ZIO[Any, Nothing, Option[String]] = ???

}
