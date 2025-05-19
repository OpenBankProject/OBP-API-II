package bootstrap.http4s.middleware

import bootstrap.http4s.CallContextKeyProvider.callContextKey
import bootstrap.http4s.RestHelperChecks._
import cats.effect._
import code.api.util.CallContext
import code.api.util.ErrorMessages.{UnknownError, UserNotLoggedIn}
import com.openbankproject.commons.model.User
import org.http4s._
import org.http4s.dsl.io._

object AuthMiddleware {

  def securedEndpoint(
    handler: (User, CallContext) => IO[Response[IO]]
  ): Request[IO] => IO[Response[IO]] = { req =>
    req.attributes.lookup(callContextKey) match {
      case Some(ctx) =>
        for {
          result <- checkAuth(req, ctx)
          (userOpt, updatedCtx) = result
          response <- userOpt match {
            case Some(user) => handler(user, updatedCtx)
            case None       => Forbidden(UserNotLoggedIn)
          }
        } yield response

      case None =>
        InternalServerError(s"$UnknownError CallContext missing")
    }
  }
}
