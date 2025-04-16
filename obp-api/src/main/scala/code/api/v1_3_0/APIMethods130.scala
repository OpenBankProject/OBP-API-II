package code.api.v1_3_0

import bootstrap.http4s.middleware.AuthMiddleware._
import cats.effect._
import code.api.Constant._
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util.{ApiRole, CallContext, CustomJsonFormats, NewStyle}
import code.api.v1_2_1.JSONFactory
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankId, User}
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}
import net.liftweb.json.{Extraction, Formats, prettyRender}
import org.http4s.HttpRoutes
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer

object APIMethods130 {

  implicit val formats: Formats = CustomJsonFormats.formats

  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val version: ApiVersion = ApiVersion.v1_3_0
  val versionStatus = ApiVersionStatus.DEPRECATED.toString
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  
  val v130Services: HttpRoutes[IO] = HttpRoutes.of[IO] {

//        resourceDocs += ResourceDoc(
//          root,
//          apiVersion,
//          "root",
//          "GET",
//          "/root",
//          "Get API Info (root)",
//          """Returns information about:
//            |
//            |* API version
//            |* Hosted by information
//            |* Git Commit""",
//          EmptyBody,
//          apiInfoJSON,
//          List(UnknownError, "no connector set"),
//          apiTagApi :: Nil)

    case req@GET -> Root / ApiPathZero / apiVersion / "root" =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val json: String = JSONFactory.getApiInfoJSON(version, versionStatus)
        Ok(json)
      }(req)
    
    case req@GET -> Root / ApiPathZero / apiVersion / "cards" =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val futureLogic = for {
          (cards, updatedCtxOpt) <- NewStyle.function.getPhysicalCardsForUser(user, Some(callContext))
          json: String = (JSONFactory1_3_0.createPhysicalCardsJSON(cards, user))
        } yield (json, updatedCtxOpt.getOrElse(callContext))
        IO.fromFuture(IO(futureLogic)).flatMap { case (json, _) => Ok(json) }
      }(req)

//        resourceDocs += ResourceDoc(
//          getCards,
//          apiVersion,
//          "getCards",
//          "GET",
//          "/cards",
//          "Get cards for the current user",
//          "Returns data about all the physical cards a user has been issued. These could be debit cards, credit cards, etc.",
//          EmptyBody,
//          physicalCardsJSON,
//          List(UserNotLoggedIn, UnknownError),
//          List(apiTagCard))
    
    case req@GET -> Root / ApiPathZero / apiVersion / "banks" / bankId / "cards" =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val futureLogic = for {
          httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
          (obpQueryParams, ctx1) <- createQueriesByHttpParamsFuture(httpParams, Some(callContext))
          _ <- NewStyle.function.hasEntitlement(bankId, user.userId, ApiRole.canGetCardsForBank, ctx1)
          (bank, ctx2) <- NewStyle.function.getBank(BankId(bankId), ctx1)
          (cards, ctx3) <- NewStyle.function.getPhysicalCardsForBank(bank, user, obpQueryParams, ctx2)
          json: String = (JSONFactory1_3_0.createPhysicalCardsJSON(cards, user))
        } yield (json, ctx3)

        IO.fromFuture(IO(futureLogic)).flatMap {
          case (json, _) => Ok(json)
        }
      }(req)

  }
}
