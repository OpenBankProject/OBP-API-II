package bootstrap.http4s

import cats.effect.IO
import cats.syntax.all._
import code.util.Helper.MdcLoggable
import org.http4s._
import org.http4s.client.Client

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

      IO(logger.debug(s"[Proxy] ${req.method} ${req.uri} -> ${proxiedUri}")).flatMap { _ =>
        client.run(proxiedRequest).use { proxyResponse =>
          IO(logger.debug(s"[Proxy] Response from ${proxiedUri}: ${proxyResponse.status}")).flatMap { _ =>
            Response[IO](
              status = proxyResponse.status,
              headers = proxyResponse.headers,
              body = proxyResponse.body
            ).pure[IO]
          }
        }
      }
  }


}
