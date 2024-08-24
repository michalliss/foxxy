package foxxy.testing

import sttp.client3.HttpClientSyncBackend
import sttp.model.Uri
import sttp.tapir.*
import sttp.tapir.DecodeResult.Value
import sttp.tapir.client.sttp.SttpClientInterpreter
import zio.*

import java.net.ServerSocket
import scala.util.{Try, Using}

case class TestClient(port: Int) {
  def send[I, E, O](endpoint: Endpoint[Unit, I, E, O, Any], params: I): Task[Either[E, O]] =
    ZIO.attempt {
      val client =
        SttpClientInterpreter().toClient(endpoint, Some(Uri.parse(s"http://localhost:${port}").right.get), HttpClientSyncBackend())
      client(params).match {
        case Value(v) => v
        case _        => throw new Exception("Unexpected result")
      }
    }
}

object TestClient {

  def findFreePort(): Try[Int] = Using(new ServerSocket(0))(_.getLocalPort)

  def send[I, E, O](endpoint: Endpoint[Unit, I, E, O, Any], params: I) = ZIO.serviceWithZIO[TestClient](_.send(endpoint, params))

  def startOnFreePort[R, E, A](startFunction: Int => ZIO[R, E, A], healthcheck: TestClient => Task[Unit]) = ZLayer.scoped(for {
    port        <- ZIO.attempt(findFreePort().get)
    testBackend <- ZIO.succeed(TestClient(port))
    _           <- startFunction(port).forkScoped
    _           <- healthcheck(testBackend).retry(Schedule.fixed(1.second))
  } yield testBackend)
}
