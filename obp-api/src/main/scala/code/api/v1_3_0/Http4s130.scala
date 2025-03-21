package code.api.v1_3_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.FutureUtil.EndpointContext
import code.api.util.NewStyle.HttpCode
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}
import code.api.util.{APIUtil, ApiRole, CallContext, CustomJsonFormats, NewStyle}
import code.api.v1_2_1.JSONFactory
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankCommons, BankId, ErrorMessage}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import cats.effect._
import org.http4s.{HttpRoutes, _}
import org.http4s.dsl.io.{InternalServerError, _}
import net.liftweb.json.Formats
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import scala.language.{higherKinds, implicitConversions}
import cats.effect._
import code.api.v4_0_0.JSONFactory400
import org.http4s._
import org.http4s.dsl.io._
import net.liftweb.json.JsonAST.{JValue, prettyRender}
import net.liftweb.json.{Extraction, MappingException, compactRender, parse}
import cats.effect._
import cats.data.Kleisli
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s._
import cats.effect.IO
import code.api.APIFailureNewStyle
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.io._
import org.typelevel.vault.Key
import code.api.Constant._
import org.http4s.dsl.io._

object Http4s130 {

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String =  prettyRender(Extraction.decompose(any))
  
  val version : ApiVersion = ApiVersion.v1_3_0 //  "1.3.0"
  val versionStatus = ApiVersionStatus.DEPRECATED.toString
  
  import cats.effect.unsafe.implicits.global
  val callContextKey: Key[CallContext] = Key.newKey[IO, CallContext].unsafeRunSync()
  
  case class ErrorResponse(message: String)
  
  object CallContextMiddleware {
  

    def withCallContext(routes: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli { req: Request[IO] =>
      val callContext = CallContext()
      val updatedAttributes = req.attributes.insert(callContextKey, callContext)
      println("why hello will come here???")
      println(req)
//      throw new RuntimeException("1313123313")
      val updatedReq = req.withAttributes(updatedAttributes)
      routes(updatedReq)
    }
  }
  
  
  val v130Services: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root /ApiPathZero / apiVersion / "root" =>
      import com.openbankproject.commons.ExecutionContext.Implicits.global
      val callContext = req.attributes.lookup(callContextKey).get.asInstanceOf[CallContext]
      Ok(IO.fromFuture(IO(
        for {
          _ <- Future() // Just start async call
        } yield {
          convertAnyToJsonString(
            JSONFactory.getApiInfoJSON(version, versionStatus)
          )
        }
      )))

    case req @ GET -> Root /ApiPathZero / apiVersion / "cards" => {
      Ok(IO.fromFuture(IO({
        val callContext = req.attributes.lookup(callContextKey).get.asInstanceOf[CallContext]
        import com.openbankproject.commons.ExecutionContext.Implicits.global
        for {
          (Full(u), callContext) <- authenticatedAccessHttp4s(req, CallContext())
          (cards, callContext) <- NewStyle.function.getPhysicalCardsForUser(u, callContext)
        } yield {
          convertAnyToJsonString(JSONFactory1_3_0.createPhysicalCardsJSON(cards, u))
        }
      })))
    }

    case req @ GET -> Root /ApiPathZero/ apiVersion / "banks" / bankId / "cards"  => {
      Ok(IO.fromFuture(IO({ 
        import com.openbankproject.commons.ExecutionContext.Implicits.global
        for {
          (Full(u), callContext) <- authenticatedAccessHttp4s(req, CallContext())
          httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
          (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, callContext)
          _ <- NewStyle.function.hasEntitlement(bankId, u.userId, ApiRole.canGetCardsForBank, callContext)
          (bank, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
          (cards, callContext) <- NewStyle.function.getPhysicalCardsForBank(bank, u, obpQueryParams, callContext)
        } yield {
          convertAnyToJsonString(JSONFactory1_3_0.createPhysicalCardsJSON(cards, u))
        }
      }))) handleErrorWith { error =>
        val apiFailureNewStyle = parse(error.getMessage).extract[APIFailureNewStyle]
        val errorMessage =  ErrorMessage(apiFailureNewStyle.failCode, apiFailureNewStyle.failMsg)
        InternalServerError(prettyRender(Extraction.decompose(errorMessage)))
      }
    }
  }

  val wrappedRoutesV130Services: HttpRoutes[IO] = CallContextMiddleware.withCallContext(v130Services)
}
