package bootstrap.http4s.middleware

import bootstrap.http4s.CallContextKeyProvider.callContextKey
import bootstrap.http4s.RestHelperChecks._
import cats.effect._
import code.api.util.CallContext
import code.api.util.ErrorMessages.{UnknownError, UserNotLoggedIn}
import com.openbankproject.commons.model.User
import org.http4s._
import org.http4s.headers.`Content-Type`

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
            case None       =>
              val errorJson = s"""{"code": 403, "message": "$UserNotLoggedIn"}"""
              IO(Response[IO](status = Status.fromInt(403).getOrElse(Status.InternalServerError))
                .withEntity(errorJson)
                .withContentType(`Content-Type`(MediaType.application.json)))
          }
        } yield response

      case None =>
        val errorJson = s"""{"code": 500, "message": "$UnknownError CallContext missing"}"""
        IO(Response[IO](status = Status.InternalServerError)
          .withEntity(errorJson)
          .withContentType(`Content-Type`(MediaType.application.json)))
        
    }
  }
}
