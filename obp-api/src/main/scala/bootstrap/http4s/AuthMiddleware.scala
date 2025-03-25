package code.api.http4s

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import code.api.util.CallContext
import com.openbankproject.commons.model.User
import code.api.http4s.AuthZChecks._
import code.api.http4s.CallContextKeyProvider.callContextKey

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
            case None       => Forbidden("User not logged in.")
          }
        } yield response

      case None =>
        InternalServerError("CallContext missing")
    }
  }
}
