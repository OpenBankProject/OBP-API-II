package code.api.http4s

import cats.data.Kleisli
import cats.effect.IO
import org.http4s._
import code.api.util.CallContext
import code.api.http4s.CallContextKeyProvider.callContextKey

object CallContextMiddleware {

  /**
   * Middleware to inject a new CallContext into every incoming request.
   * This allows downstream routes to retrieve CallContext from Vault attributes.
   */
  def withCallContext(routes: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli { req: Request[IO] =>
    val callContext = CallContext() // Create a new CallContext for each request
    val updatedReq = req.withAttributes(
      req.attributes.insert(callContextKey, callContext) // Inject into request attributes
    )
    routes(updatedReq)
  }
}
