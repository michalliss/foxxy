package foxxy.shared

import sttp.model.StatusCode
import sttp.tapir._
import zio.json._

object BaseEndpoints {
  val publicEndpoint = endpoint.errorOut(
    oneOf[DefaultErrors](
      oneOfVariant(statusCode(StatusCode.Unauthorized).and(stringBody.mapTo[Unauthorized])),
      oneOfVariant(statusCode(StatusCode.Forbidden).and(stringBody.mapTo[Forbidden])),
      oneOfVariant(statusCode(StatusCode.BadRequest).and(stringBody.mapTo[BadRequest])),
      oneOfVariant(statusCode(StatusCode.NotFound).and(stringBody.mapTo[NotFound])),
      oneOfVariant(statusCode(StatusCode.InternalServerError).and(stringBody.mapTo[InternalServerError])),
      oneOfDefaultVariant(stringBody.mapTo[Unknown])
    )
  )

  val secureEndpoint = publicEndpoint
    .securityIn(auth.bearer[String]())
}

sealed trait DefaultErrors                  extends Throwable
case class Unauthorized(msg: String)        extends DefaultErrors derives JsonCodec
case class Forbidden(msg: String)           extends DefaultErrors derives JsonCodec
case class BadRequest(msg: String)          extends DefaultErrors derives JsonCodec
case class NotFound(msg: String)            extends DefaultErrors derives JsonCodec
case class InternalServerError(msg: String) extends DefaultErrors derives JsonCodec
case class Unknown(msg: String)             extends DefaultErrors derives JsonCodec

case class DefaultErrorThrowable(defaultErrors: DefaultErrors) extends Throwable
