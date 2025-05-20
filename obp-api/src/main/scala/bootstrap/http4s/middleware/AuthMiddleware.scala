package bootstrap.http4s.middleware

import bootstrap.http4s.CallContextKeyProvider.callContextKey
import bootstrap.http4s.RestHelperChecks._
import cats.effect.IO
import code.api.util.APIUtil.nameOfSpellingParam
import code.api.util.CallContext
import code.api.util.ErrorMessages.{UnknownError, UserNotLoggedIn}
import com.openbankproject.commons.model.User
import net.liftweb.common._
import net.liftweb.http.provider.HTTPParam
import org.http4s.{Request, _}
import org.http4s.headers.`Content-Type`

import java.util.UUID
import scala.collection.immutable.List

object AuthMiddleware {

  def securedEndpoint(
    handler: (User, CallContext) => IO[Response[IO]]
  ): Request[IO] => IO[Response[IO]] = { req =>
    req.attributes.lookup(callContextKey) match {
      case Some(cc) =>
        val url = java.net.URLDecoder.decode(req.uri.renderString, "UTF-8")
        val verb = req.method.name
        val reqHeaders = req.headers.headers.map(h => HTTPParam(h.name.toString, List(h.value)))
        val authorizationHeaderValue = reqHeaders.find(_.name.equalsIgnoreCase("Authorization")).flatMap(_.values.headOption)
        val params: Map[String, String] = req.uri.query.params
        val ipAddress = req.remote.map(_.host.toString).getOrElse("")
        val correlationId = UUID.randomUUID().toString
        val sessonId = UUID.randomUUID().toString
        
        val spellingHeader = reqHeaders.find(_.name.equalsIgnoreCase(nameOfSpellingParam()))
        val spellingHeaderValue = spellingHeader.flatMap(_.values.headOption)
        
//        val body: Box[String] = req.as[String].attempt.unsafeRunSync() match {
//          case Right(value) => Full(value)
//          case Left(_)      => Empty
//        }
        val body: Box[String] = Full("")
        
        val ctx = cc.copy(
          url = url,
          httpBody = body,
          spelling = spellingHeaderValue,
          verb = verb,
          authReqHeaderField = authorizationHeaderValue,
          directLoginParams = params,
          oAuthParams = params,
          requestHeaders = reqHeaders,
          ipAddress = ipAddress,
          correlationId = correlationId,
          sessionId = Some(sessonId),
          implementedInVersion = "1.3.0",//TODO,this should be from resourceDoc
        )
        
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
