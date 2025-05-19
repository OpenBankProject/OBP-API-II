package bootstrap.http4s.middleware

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.APIFailureNewStyle
import code.api.util.{CallContext, CustomJsonFormats}
import com.openbankproject.commons.model.ErrorMessage
import net.liftweb.json.{Extraction, compactRender, parse}
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.{HttpRoutes, MediaType, _}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.Future

object JsonErrorHandlerMiddleware {
  
  implicit val formats = CustomJsonFormats.formats
  
  private val logger = Slf4jLogger.getLogger[IO]

  def executeWithErrorHandling(
    logic: => Future[(String, Option[CallContext])]
  ): IO[Response[IO]] = {
    import net.liftweb.json._
    implicit val formats = DefaultFormats

    IO.fromFuture(IO(logic)).attempt.flatMap {
      case Right((json, _)) =>
        Ok(json).map(_.withContentType(`Content-Type`(MediaType.application.json)))


      case Left(error) =>
        val (code, msg) = try {
          val parsed = parse(error.getMessage)
          val failMsg = (parsed \ "failMsg").extractOpt[String].getOrElse("Unknown error")
          val httpCode = (parsed \ "ccl" \ "httpCode").extractOpt[Int]
          val failCode = (parsed \ "failCode").extractOpt[Int].getOrElse(500)
          val code = httpCode.getOrElse(failCode)
          (code, failMsg)
        } catch {
          case _: Throwable => (500, error.getMessage)
        }

        val errorJson = s"""{"code": $code, "message": "${msg.replace("\"", "\\\"")}"}"""
        IO(Response[IO](status = Status.fromInt(code).getOrElse(Status.InternalServerError))
          .withEntity(errorJson)
          .withContentType(`Content-Type`(MediaType.application.json)))
    }
  }


  def apply(routes: HttpRoutes[IO]): HttpRoutes[IO] =
    Kleisli { req =>
      OptionT {
        routes(req).value.handleErrorWith { error =>
          val stackTrace = error.getStackTrace.mkString("\n")
          logger.error(s"[${error.toString}]-Message:${error.getMessage} at ${req.method} ${req.uri}\n Error StackTrace: $stackTrace") *> IO {
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
