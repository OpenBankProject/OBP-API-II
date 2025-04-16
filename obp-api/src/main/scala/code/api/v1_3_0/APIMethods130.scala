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
import cats.syntax.all._

import scala.collection.mutable.ArrayBuffer

object APIMethods130 {

  implicit val formats: Formats = CustomJsonFormats.formats

  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val version: ApiVersion = ApiVersion.v1_3_0
  val versionStatus = ApiVersionStatus.DEPRECATED.toString
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  object Implementations1_3_0 {

    // Common prefix: /obp/v1.3.0
    val prefixPath = Root / ApiPathZero.toString / version.toString

//    resourceDocs += ResourceDoc(
//      root,
//      apiVersion,
//      "root",
//      "GET",
//      "/root",
//      "Get API Info (root)",
//      """Returns information about:
//        |
//        |* API version
//        |* Hosted by information
//        |* Git Commit""",
//      EmptyBody,
//      apiInfoJSON,
//      List(UnknownError, "no connector set"),
//      apiTagApi :: Nil)
    
    // Route: GET /obp/v1.3.0/root
    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req@GET -> `prefixPath` / "root" =>
        val json: String = JSONFactory.getApiInfoJSON(version, versionStatus)
        Ok(json)
    }

//    resourceDocs += ResourceDoc(
//      getCards,
//      apiVersion,
//      "getCards",
//      "GET",
//      "/cards",
//      "Get cards for the current user",
//      "Returns data about all the physical cards a user has been issued. These could be debit cards, credit cards, etc.",
//      EmptyBody,
//      physicalCardsJSON,
//      List(UserNotLoggedIn, UnknownError),
//      List(apiTagCard))
    
    // Route: GET /obp/v1.3.0/cards
    val getCardsRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "cards" =>
        securedEndpoint { (user: User, callContext: CallContext) =>
          val logic = for {
            (cards, updatedCtx) <- NewStyle.function.getPhysicalCardsForUser(user, Some(callContext))
            json: String = JSONFactory1_3_0.createPhysicalCardsJSON(cards, user)
          } yield (json, updatedCtx.getOrElse(callContext))
          IO.fromFuture(IO(logic)).flatMap { case (json, _) => Ok(json) }
        }(req)
    }


//    resourceDocs += ResourceDoc(
//      getCardsForBank,
//      apiVersion,
//      "getCardsForBank",
//      "GET",
//      "/banks/BANK_ID/cards",
//      "Get cards for the specified bank",
//      "",
//      EmptyBody,
//      physicalCardsJSON,
//      List(UserNotLoggedIn,BankNotFound, UnknownError),
//      List(apiTagCard))
    // Route: GET /obp/v1.3.0/banks/BANK_ID/cards
    val getCardsForBankRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "cards" =>
        securedEndpoint { (user: User, callContext: CallContext) =>
          val logic = for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (queryParams, ctx1) <- createQueriesByHttpParamsFuture(httpParams, Some(callContext))
            _ <- NewStyle.function.hasEntitlement(BankId(bankId), user.userId, ApiRole.canGetCardsForBank, ctx1)
            (bank, ctx2) <- NewStyle.function.getBank(BankId(bankId), ctx1)
            (cards, ctx3) <- NewStyle.function.getPhysicalCardsForBank(bank, user, queryParams, ctx2)
            json: String = JSONFactory1_3_0.createPhysicalCardsJSON(cards, user)
          } yield (json, ctx3)
          IO.fromFuture(IO(logic)).flatMap { case (json, _) => Ok(json) }
        }(req)
    }

    // All routes combined
    val allRoutes: HttpRoutes[IO] =
      root <+> 
        getCardsRoute <+> 
        getCardsForBankRoute
  }
 
}
