package foxxy.reference.frontend.services

import com.raquo.waypoint.Router
import foxxy.reference.frontend.Page
import foxxy.shared.{DefaultErrors, Unauthorized}
import sttp.model.Uri
import sttp.tapir.Endpoint
import zio.*
import zio.json.JsonCodec

case class MyHttpClient(storage: Storage, router: Router[Page], authSerivce: AuthService) {

  def fetchToken  = storage.get[String]("token")
  def backendHost = "localhost:5004"

  object extensions {
    extension [T1, T2, T3](endpoint: Endpoint[String, T1, DefaultErrors, T3, Any])
      def sendSecure = {
        foxxy.frontend.utils
          .sendSecure(endpoint)(Uri(backendHost))(fetchToken.getOrElse("no_token"))
          .andThen(x => x.tapSome { case Left(Unauthorized(msg)) => ZIO.attempt { authSerivce.logout; router.pushState(Page.Login) } })
      }

    extension [T1, T2, T3](endpoint: Endpoint[Unit, T1, DefaultErrors, T3, Any])
      def send = {
        foxxy.frontend.utils.send(endpoint)(Uri(backendHost))
      }
  }

}
