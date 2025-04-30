package bootstrap.http4s

import cats.effect.IO
import cats.syntax.all._
import code.api.util.ErrorMessages
import code.util.Helper.MdcLoggable
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`

class ObpApiDispatch(client: Client[IO], obpApiBaseUri: Uri) extends MdcLoggable{

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req =>
      val queryParams: Map[String, String] =
        req.uri.query.pairs.collect {
          case (k, Some(v)) => k -> v
        }.toMap

      val proxiedUri = obpApiBaseUri
        .withPath(req.uri.path)
        .withQueryParams(queryParams)

      val proxiedRequest = Request[IO](
        method = req.method,
        uri = proxiedUri,
        httpVersion = req.httpVersion,
        headers = req.headers,
        body = req.body
      )

      IO(logger.debug(s"[Proxy] ${req.method} ${req.uri} -> ${proxiedUri}")) *>
        client.run(proxiedRequest).use { proxyResponse =>
          IO(logger.debug(s"[Proxy] Response from ${proxiedUri}: ${proxyResponse.status}")) *>
            Response[IO](
              status = proxyResponse.status,
              headers = proxyResponse.headers,
              body = proxyResponse.body
            ).pure[IO]
        }.handleErrorWith { ex => 
          IO(logger.error(s"[Proxy] Error proxying to OBP API: ${ex.getMessage}", ex)) *>
            Response[IO](
              status = Status.InternalServerError
            ).withEntity(s"""{"message": "${ErrorMessages.UnknownError} Please check if OBP-API is dead, details:${ex.getMessage} "}""")
              .withContentType(`Content-Type`(MediaType.application.json))
              .pure[IO]
        }
  }

}
