package bootstrap.http4s

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.FutureUtil.EndpointContext
import code.api.util.NewStyle.HttpCode
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}
import code.api.util.{APIUtil, ApiRole, CallContext, CustomJsonFormats, NewStyle}
import code.api.v1_2_1.JSONFactory
import code.api.http4s.AuthMiddleware._
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankId, ErrorMessage, User}
import net.liftweb.common.Full
import scala.concurrent.Future
import cats.effect._
import org.http4s.{HttpRoutes, _}
import org.http4s.dsl.io._
import net.liftweb.json.{Extraction, Formats, JsonAST, MappingException, parse, prettyRender}
import code.api.v1_3_0.JSONFactory1_3_0
import code.api.APIFailureNewStyle
import code.api.Constant._
import code.api.ResourceDocs1_4_0.ResourceDocs140.ImplementationsResourceDocs
import net.liftweb.http.LiftResponse
import net.liftweb.actor.LAFuture
import bootstrap.http4s.LiftCompatUtils.createLiftRequestObject 


object Http4s130 {

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val version: ApiVersion = ApiVersion.v1_3_0
  val versionStatus = ApiVersionStatus.DEPRECATED.toString


  val v130Services: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req @ GET -> Root / "resource-docs"/ "v5.1.0" / "obp"  =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint(ImplementationsResourceDocs.getResourceDocsObpV400, liftRequest, callContext)
        
        IO.fromFuture(IO(Future{liftResponse})).flatMap {
          case (json) => Ok(json.toString)
        }
        
      }(req)
      
    case req @ GET -> Root / ApiPathZero / apiVersion / "root" =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val futureLogic = Future {
          val json :String = JSONFactory.getApiInfoJSON(version, versionStatus)
          (json, callContext)
        }
        IO.fromFuture(IO(futureLogic)).flatMap {
          case (json, _) => Ok(json)
        }
      }(req)


    case req @ GET -> Root / ApiPathZero / apiVersion / "cards" =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val futureLogic = for {
          (cards, updatedCtxOpt) <- NewStyle.function.getPhysicalCardsForUser(user, Some(callContext))
          json :String = (JSONFactory1_3_0.createPhysicalCardsJSON(cards, user))
        } yield (json, updatedCtxOpt.getOrElse(callContext))
        IO.fromFuture(IO(futureLogic)).flatMap { case (json, _) => Ok(json) }
      }(req)

    case req @ GET -> Root / ApiPathZero / apiVersion / "banks" / bankId / "cards" =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val futureLogic = for {
          httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
          (obpQueryParams, ctx1) <- createQueriesByHttpParamsFuture(httpParams, Some(callContext))
          _ <- NewStyle.function.hasEntitlement(bankId, user.userId, ApiRole.canGetCardsForBank, ctx1)
          (bank, ctx2) <- NewStyle.function.getBank(BankId(bankId), ctx1)
          (cards, ctx3) <- NewStyle.function.getPhysicalCardsForBank(bank, user, obpQueryParams, ctx2)
          json:String =(JSONFactory1_3_0.createPhysicalCardsJSON(cards, user))
        } yield (json, ctx3)

        IO.fromFuture(IO(futureLogic)).flatMap {
          case (json, _) => Ok(json)
        }
      }(req)
  }
}
