package bootstrap.http4s.middleware

import cats.data.Kleisli
import cats.effect.IO
import org.http4s._


object ResponseMiddleware {

  // Middleware that sets the 'Content-Type' header and status code for all responses
  def contentTypeMiddleware(httpRoutes: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli { request =>
    httpRoutes(request).map { response =>
      response.withHeaders(Header("Content-Type", "application/json")) // Set the Content-Type header
        .withStatus(Status.Ok) // Set default HTTP status code
    }
  }
}