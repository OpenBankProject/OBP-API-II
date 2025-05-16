package bootstrap.http4s.test

import bootstrap.http4s.LiftCompatUtils.createLiftRequestObject
import bootstrap.http4s.middleware.AuthMiddleware._
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.ResourceDocs140.ImplementationsResourceDocs
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util.{ApiRole, CallContext, CustomJsonFormats, NewStyle}
import code.api.v1_2_1.JSONFactory
import code.api.v1_3_0.JSONFactory1_3_0
import code.bankconnectors.Connector
import com.openbankproject.commons.model.{BankId, User}
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}
import net.liftweb.json.{Extraction, Formats, prettyRender}
import org.http4s.HttpRoutes
import org.http4s.dsl.io._


object Http4sObpMigrationTest {

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val version: ApiVersion = ApiVersion.v1_3_0
  val versionStatus = ApiVersionStatus.DEPRECATED.toString

  val v130Services: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req@GET -> Root / "test" /ApiPathZero / apiVersion / "rootIO" =>
      securedEndpoint { (user: User, callContext: CallContext) =>

        val json: IO[String] = JSONFactory.getApiInfoJSONIO(version, versionStatus).map(convertAnyToJsonString)

        Ok(json)

      }(req)
      
      
    case req @ POST -> Root /"dynamic-resource-doc"/"test"/"my_user"/"MY_USER_ID" =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint(code.api.dynamic.endpoint.helper.DynamicEndpoints.dynamicEndpoint, liftRequest, callContext)
        IO.fromFuture(IO(liftResponse)).flatMap {
          case (json) => Ok(json._1.toString)
        }
      }(req)
      
    case req @ POST -> Root / "management"/ "dynamic-resource-docs" =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint(code.api.v4_0_0.APIMethods400.Implementations4_0_0.createDynamicResourceDoc, liftRequest, callContext)
        IO.fromFuture(IO(liftResponse)).flatMap {
          case (json) => Ok(json._1.toString)
        }
      }(req)
      
    case req @ GET -> Root / "banks" / "gh.29.uk" / "FooBar01" =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint(code.api.dynamic.entity.APIMethodsDynamicEntity.ImplementationsDynamicEntity.genericEndpoint, liftRequest, callContext)
        IO.fromFuture(IO(liftResponse)).flatMap {
          case (json) => Ok(json._1.toString)
        }
      }(req)
    case req @ POST -> Root / "management" / "system-dynamic-entities"  =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint(code.api.v4_0_0.APIMethods400.Implementations4_0_0.createSystemDynamicEntity, liftRequest, callContext)
        IO.fromFuture(IO(liftResponse)).flatMap {
          case (json) => Ok(json._1.toString)
        }
      }(req)
      
    case req @ POST -> Root / "management" / "dynamic-endpoints2"  =>
      securedEndpoint { (user: User, callContext: CallContext) =>
//        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint2(code.api.v4_0_0.APIMethods400.Implementations4_0_0.createDynamicEndpoint2, req, callContext)
        IO.fromFuture(IO(liftResponse)).flatMap {
          case (json) => Ok(json._1.toString)
        }
      }(req)
      
    case req @ POST -> Root  / "management" / "dynamic-endpoints"  =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint(code.api.v4_0_0.APIMethods400.Implementations4_0_0.createDynamicEndpoint, liftRequest, callContext)
        IO.fromFuture(IO(liftResponse)).flatMap {
          case (json) => Ok(json._1.toString)
        }
      }(req)
      
    case req @ POST -> Root /"api"/ "v1" / "products" / "PRODUCT_CODE" / "quote"  =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint(code.api.dynamic.endpoint.APIMethodsDynamicEndpoint.ImplementationsDynamicEndpoint.dynamicEndpoint, liftRequest, callContext)
        IO.fromFuture(IO(liftResponse)).flatMap {
          case (json) => Ok(json._1.toString)
        }
      }(req)
      
    case req @ GET -> Root / "banks" / "gh.29.uk" / "FooBar01" / fooBarId =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint(code.api.dynamic.entity.APIMethodsDynamicEntity.ImplementationsDynamicEntity.genericEndpoint, liftRequest, callContext)
        IO.fromFuture(IO(liftResponse)).flatMap {
          case (json) => Ok(json._1.toString)
        }
      }(req)
      
    case req @ DELETE -> Root / "banks" / "gh.29.uk" / "FooBar01" / fooBarId =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint(code.api.dynamic.entity.APIMethodsDynamicEntity.ImplementationsDynamicEntity.genericEndpoint, liftRequest, callContext)
        IO.fromFuture(IO(liftResponse)).flatMap {
          case (json) => Ok(json._1.toString)
        }
      }(req)
      
    case req @ PUT -> Root / "banks" / "gh.29.uk" / "FooBar01" / fooBarId =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint(code.api.dynamic.entity.APIMethodsDynamicEntity.ImplementationsDynamicEntity.genericEndpoint, liftRequest, callContext)
        IO.fromFuture(IO(liftResponse)).flatMap {
          case (json) => Ok(json._1.toString)
        }
      }(req)
      
    case req @ GET -> Root / "resource-docs"/ "v5.1.0" / "obp"  =>
      securedEndpoint { (user: User, callContext: CallContext) =>
        val liftRequest = createLiftRequestObject(req)
        val liftResponse = callLiftEndpoint(ImplementationsResourceDocs.getResourceDocsObpV400, liftRequest, callContext)
        IO.fromFuture(IO(liftResponse)).flatMap {
          case (json) => Ok(json._1.toString)
        }
      }(req)
      
//    case req @ GET -> Root / "resource-docs"/ "v5.1.0" / "obp"  =>
//      securedEndpoint { (user: User, callContext: CallContext) =>
//        val liftRequest = createLiftRequestObject(req)
//        val liftResponse = callLiftEndpoint(ImplementationsResourceDocs.getResourceDocsObpV400, liftRequest, callContext)
//        //        IO.fromFuture(IO(Future{liftResponse.head})).flatMap {
////          case (json) => Ok(json.json.toString())
////        }
//        Ok(liftResponse.head.json.toJsCmd)
//        
//      }(req)
    

    case GET -> Root  /"test"/ ApiPathZero / apiVersion / "banks"  =>
      val banks = Connector.connector.vend.getBanksLegacy(None).map(_._1).openOrThrowException("xxxxx")
      Ok(prettyRender(Extraction.decompose(banks)))

 
    case req @ GET ->  Root /"test"/ ApiPathZero / apiVersion / "cardsIO" =>
      securedEndpoint { (user: User, callContext: CallContext) =>

        val ioLogic = for {
          result <- Connector.connector.vend.getPhysicalCardsForUserIO(user, Some(callContext))
          (cards, updatedCtxOpt) = (unboxFullOrFail(result._1, Some(callContext), s"$CardNotFound"), result._2)
          json:String = JSONFactory1_3_0.createPhysicalCardsJSON(cards, user)
        } yield (json, updatedCtxOpt.getOrElse(callContext))

        ioLogic.
          flatMap { 
            case (json, _) => 
              Ok(json)
          }
      }(req)

    case req @ GET -> Root /"test"/ ApiPathZero / apiVersion / "banks" / bankId / "cardsIO" =>
      securedEndpoint { (user: User, callContext: CallContext) =>
         val ioLogic = for {
           httpParams <- NewStyle.function.extractHttpParamsFromUrlIO(req.uri.renderString)
           obpQueryParamsWithCtx <- createQueriesByHttpParamsIO(httpParams, Some(callContext))
           (obpQueryParams, ctx1) = obpQueryParamsWithCtx
           _ <- NewStyle.function.hasEntitlementIO(bankId, user.userId, ApiRole.canGetCardsForBank, ctx1)
           result <- NewStyle.function.getBankIO(BankId(bankId), ctx1) 
           (bank, ctx2)= result 
           result <- NewStyle.function.getPhysicalCardsForBankIO(bank, user, obpQueryParams, ctx2)
          (cards, ctx3) = result
          json:String =JSONFactory1_3_0.createPhysicalCardsJSON(cards, user)
        } yield (json, ctx3)
        ioLogic.
          flatMap {
            case (json, _) =>
              Ok(json)
          }
      }(req)
  }
}
