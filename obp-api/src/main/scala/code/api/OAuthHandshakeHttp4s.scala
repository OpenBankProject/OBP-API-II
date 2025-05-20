package code.api

import bootstrap.http4s.middleware.AuthMiddleware._
import cats.effect.IO
import cats.syntax.all._
import code.api.oauth1a.OauthParams._
import code.api.util.CallContext
import code.token.Tokens
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import net.liftweb.common._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`

import scala.language.higherKinds

/**
 * This object provides the API calls necessary to third party applications
 * so they could authenticate their users.
 * Http4s implementation of OAuth1.0 endpoints.
 */
object OAuthHandshakeHttp4s extends MdcLoggable {

  // Common prefix: /oauth
  val prefixPath = Root / "oauth"


  val initiateRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req@POST -> `prefixPath` / "initiate" =>
      securedEndpoint { (_: User, callContext: CallContext) =>
        for {
          validationResult <- IO.fromFuture(IO(OAuthHandshake.validatorFuture("requestToken", "POST", callContext)))
          (httpCode, message, oAuthParameters) = validationResult
          response <- if (httpCode == 200) {
            val (token, secret) = OAuthHandshake.generateTokenAndSecret()
            val saved = OAuthHandshake.saveRequestToken(oAuthParameters, token, secret)
            if (saved) {
              val responseMessage = s"oauth_token=$token&oauth_token_secret=$secret&oauth_callback_confirmed=true"
              Ok(responseMessage).map(_.withContentType(`Content-Type`(MediaType.application.`x-www-form-urlencoded`)))
            } else {
              InternalServerError("Failed to save request token")
            }
          } else {
            Status.fromInt(httpCode).fold(
              _ => InternalServerError(message),
              status => IO.pure(Response[IO](status).withEntity(message))
            )
          }
        } yield response
      }(req)
  }

  val tokenRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req@POST -> `prefixPath` / "token" =>
      securedEndpoint { (_: User, callContext: CallContext) =>
        for {
          validationResult <- IO.fromFuture(IO(OAuthHandshake.validatorFuture("authorizationToken", "POST", callContext)))
          (httpCode, message, oAuthParameters) = validationResult
          response <- if (httpCode == 200) {
            val (token, secret) = OAuthHandshake.generateTokenAndSecret()
            val saved = OAuthHandshake.saveAuthorizationToken(oAuthParameters, token, secret)
            if (saved) {
              Tokens.tokens.vend.getTokenByKey(oAuthParameters.get(TokenName).get) match {
                case Full(requestToken) => Tokens.tokens.vend.deleteToken(requestToken.id.get) match {
                  case true =>
                  case false => logger.warn("Request token: " + requestToken + " is not deleted. The application could exchange it again to get an other access token!")
                }
                case _ => logger.warn("Request token is not deleted due to absence in database!")
              }
              val responseMessage = s"oauth_token=$token&oauth_token_secret=$secret"
              Ok(responseMessage).map(_.withContentType(`Content-Type`(MediaType.application.`x-www-form-urlencoded`)))
            } else {
              InternalServerError("Failed to save authorization token")
            }
          } else {
            Status.fromInt(httpCode).fold(
              _ => InternalServerError(message),
              status => IO.pure(Response[IO](status).withEntity(message))
            )
          }
        } yield response
      }(req)
  }
  // All routes combined
  val allRoutes: HttpRoutes[IO] =
    initiateRoute <+> tokenRoute
}
