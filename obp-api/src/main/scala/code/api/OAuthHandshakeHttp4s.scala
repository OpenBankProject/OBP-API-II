package code.api

import bootstrap.http4s.CallContextKeyProvider.callContextKey
import cats.effect._
import code.api.oauth1a.OauthParams._
import code.api.util.ErrorMessages.UnknownError
import code.token.Tokens
import code.util.Helper.MdcLoggable
import net.liftweb.common._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s._

/**
 * This object provides the API calls necessary to third party applications
 * so they could authenticate their users.
 * Http4s implementation of OAuth1.0 endpoints.
 */
object OAuthHandshakeHttp4s extends MdcLoggable {

  // Common prefix: /oauth
  val prefixPath = Root / "oauth"

  // Route: POST /oauth/initiate
  val initiateRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `prefixPath` / "initiate" =>
      IO.defer {
        logger.debug("Hello from oauth/initiate POST")

        // Extract the CallContext from the request attributes
        req.attributes.lookup(callContextKey) match {
          case Some(callContext) =>
            for {
              // Extract the OAuth parameters from the header and test if the request is valid
              validationResult <- IO.fromFuture(IO(OAuthHandshake.validatorFuture("requestToken", "POST", callContext)))
              (httpCode, message, oAuthParameters) = validationResult
              response <- if (httpCode == 200) {
                // Generate the token and secret
                val (token, secret) = OAuthHandshake.generateTokenAndSecret()
                // Save the token that we have generated
                val saved = OAuthHandshake.saveRequestToken(oAuthParameters, token, secret)
                if (saved) {
                  val responseMessage = s"oauth_token=$token&oauth_token_secret=$secret&oauth_callback_confirmed=true"
                  Ok(responseMessage).map(_.withContentType(`Content-Type`(MediaType.application.`x-www-form-urlencoded`)))
                } else {
                  InternalServerError("Failed to save request token")
                }
              } else {
                // Return an error response
                Status.fromInt(httpCode).fold(
                  _ => InternalServerError(message),
                  status => IO.pure(Response[IO](status).withEntity(message))
                )
              }
            } yield response
          case None =>
            InternalServerError(s"$UnknownError CallContext missing")
        }
      }
  }

  // Route: POST /oauth/token
  val tokenRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `prefixPath` / "token" =>
      IO.defer {
        // Extract the CallContext from the request attributes
        req.attributes.lookup(callContextKey) match {
          case Some(callContext) =>
            for {
              // Extract the OAuth parameters from the header and test if the request is valid
              validationResult <- IO.fromFuture(IO(OAuthHandshake.validatorFuture("authorizationToken", "POST", callContext)))
              (httpCode, message, oAuthParameters) = validationResult
              response <- if (httpCode == 200) {
                // Generate the token and secret
                val (token, secret) = OAuthHandshake.generateTokenAndSecret()
                // Save the token that we have generated
                val saved = OAuthHandshake.saveAuthorizationToken(oAuthParameters, token, secret)
                if (saved) {
                  // Remove the request token so the application could not exchange it
                  // again to get another access token
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
                // Return an error response
                Status.fromInt(httpCode).fold(
                  _ => InternalServerError(message),
                  status => IO.pure(Response[IO](status).withEntity(message))
                )
              }
            } yield response
          case None =>
            InternalServerError(s"$UnknownError CallContext missing")
        }
      }
  }

  // All routes combined
  val allRoutes: HttpRoutes[IO] =
    initiateRoute 
  //<+> tokenRoute
}
