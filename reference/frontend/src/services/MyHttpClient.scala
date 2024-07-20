package foxxy.reference.frontend.services

import foxxy.frontend.utils.*
import sttp.model.Uri
import sttp.tapir.Endpoint
import zio.* 
import zio.json.JsonCodec
import foxxy.shared.DefaultErrors
import foxxy.shared.Unauthorized
import com.raquo.waypoint.Router
import foxxy.reference.frontend.Page

case class MyHttpClient(storage: Storage, router: Router[Page]) {

  def fetchToken  = storage.get[String]("token")
  def backendHost = "localhost:5004"

  object extensions {
    extension [T1, T2, T3](endpoint: Endpoint[String, T1, DefaultErrors, T3, Any])
      def sendSecure = {
        foxxy.frontend.utils
        .sendSecure(endpoint)(Uri(backendHost))(fetchToken.getOrElse("no_token"))
        .andThen(x => x.tapSome{ case Left(Unauthorized(msg)) => ZIO.attempt(router.pushState(Page.Login))})
      }

    extension [T1, T2, T3](endpoint: Endpoint[Unit, T1, DefaultErrors, T3, Any])
      def send = {
        foxxy.frontend.utils.send(endpoint)(Uri(backendHost))
      }
  }

}
