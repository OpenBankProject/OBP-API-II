package bootstrap.http4s.middleware

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import org.typelevel.log4cats.slf4j.Slf4jLogger
import net.liftweb.json.{parse, Extraction, compactRender}
import code.api.APIFailureNewStyle
import code.api.util.ErrorMessages
import code.api.util.{APIUtil, CustomJsonFormats}
import com.openbankproject.commons.model.ErrorMessage

object JsonErrorHandler {
  
  implicit val formats = CustomJsonFormats.formats
  
  private val logger = Slf4jLogger.getLogger[IO]

  def apply(routes: HttpRoutes[IO]): HttpRoutes[IO] =
    Kleisli { req =>
      OptionT {
        routes(req).value.handleErrorWith { error =>
          val stackTrace = error.getStackTrace.mkString("\n")
          logger.error(s"[Error] ${error.getMessage} at ${req.method} ${req.uri}\n$stackTrace") *> IO {
            try {
              val failure = parse(error.getMessage).extract[APIFailureNewStyle]
              val errorMessage = ErrorMessage(failure.failCode, failure.failMsg)
              val json = compactRender(Extraction.decompose(errorMessage))
              Some(Response[IO](status = Status.InternalServerError)
                .withEntity(json)
                .withContentType(`Content-Type`(MediaType.application.json)))
            } catch {
              case _: Throwable =>
                val safeMsg = error.getMessage.replace("\"", "\'")
                val fallbackJson = s"""{"message": "$safeMsg"}"""
                Some(Response[IO](status = Status.InternalServerError)
                  .withEntity(fallbackJson)
                  .withContentType(`Content-Type`(MediaType.application.json)))
            }
          }
        }
      }
    }
}
