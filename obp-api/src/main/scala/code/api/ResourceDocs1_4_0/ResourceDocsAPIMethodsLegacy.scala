package code.api.ResourceDocs1_4_0

import code.api.Constant.GET_DYNAMIC_RESOURCE_DOCS_TTL
import code.api.OBPRestHelper
import code.api.ResourceDocs1_4_0.ResourceDocsAPIMethodsUtil._
import code.api.cache.Caching
import code.api.util.APIUtil._
import code.api.util.ApiRole.{canReadDynamicResourceDocsAtOneBank, canReadResourceDoc}
import code.api.util.ApiTag._
import code.api.util.FutureUtil.EndpointContext
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.v1_4_0.{APIMethods140, JSONFactory1_4_0}
import code.api.v2_2_0.APIMethods220
import code.apicollectionendpoint.MappedApiCollectionEndpointsProvider
import code.util.Helper
import code.util.Helper.{MdcLoggable, SILENCE_IS_GOLDEN}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.ContentParam.{DYNAMIC, STATIC}
import com.openbankproject.commons.model.{BankId, User}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Box, Full}
import net.liftweb.json

import scala.collection.immutable.{List, Nil}
import scala.concurrent.Future

// JObject creation
import code.api.v1_2_1.APIMethods121
//import code.api.v1_3_0.{APIMethods130, OBPAPI1_3_0}
import code.api.v2_0_0.APIMethods200
import code.api.v2_1_0.APIMethods210

import scala.collection.mutable.ArrayBuffer

// So we can include resource docs from future versions
import code.api.util.ErrorMessages._
import com.openbankproject.commons.ExecutionContext.Implicits.global


trait ResourceDocsAPIMethodsLegacy extends MdcLoggable with APIMethods220 with APIMethods210 with APIMethods200 with APIMethods140  with APIMethods121{
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  // We add previous APIMethods so we have access to the Resource Docs
  self: OBPRestHelper =>

  val ImplementationsResourceDocs = new Object() {

    val localResourceDocs = ArrayBuffer[ResourceDoc]()

    val implementedInApiVersion = ApiVersion.v1_4_0

    implicit val formats = CustomJsonFormats.rolesMappedToClassesFormats
    
    localResourceDocs += ResourceDoc(
      getResourceDocsObp,
      implementedInApiVersion,
      "getResourceDocsObp",
      "GET",
      "/resource-docs/API_VERSION/obp",
      "Get Resource Docs.",
      getResourceDocsDescription(false),
      EmptyBody,
      EmptyBody, 
      UnknownError :: Nil,
      List(apiTagDocumentation, apiTagApi),
      Some(List(canReadResourceDoc))
    )

    // Provides resource documents so that API Explorer (or other apps) can display API documentation
    // Note: description uses html markup because original markdown doesn't easily support "_" and there are multiple versions of markdown.
    lazy val getResourceDocsObp : OBPEndpointFuture = {
      case "resource-docs" :: requestedApiVersionString :: "obp" :: Nil JsonGet _ => {
        val (tags, partialFunctions, locale, contentParam, apiCollectionIdParam) = ResourceDocsAPIMethodsUtil.getParams()
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          getApiLevelResourceDocs(cc,requestedApiVersionString, tags, partialFunctions, locale, contentParam, apiCollectionIdParam,false)
      }
    }
    
    localResourceDocs += ResourceDoc(
      getResourceDocsObpV400,
      implementedInApiVersion,
      nameOf(getResourceDocsObpV400),
      "GET",
      "/resource-docs/API_VERSION/obp",
      "Get Resource Docs",
      getResourceDocsDescription(false),
      EmptyBody,
      EmptyBody,
      UnknownError :: Nil,
      List(apiTagDocumentation, apiTagApi),
      Some(List(canReadResourceDoc))
    )
    
    
    lazy val getResourceDocsObpV400 : OBPEndpointFuture = {
      case "resource-docs" :: requestedApiVersionString :: "obp" :: Nil JsonGet _ => {
        val (tags, partialFunctions, locale, contentParam, apiCollectionIdParam) = ResourceDocsAPIMethodsUtil.getParams()
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          getApiLevelResourceDocs(cc,requestedApiVersionString, tags, partialFunctions, locale, contentParam, apiCollectionIdParam,true)
      }
    }
    
    localResourceDocs += ResourceDoc(
      getBankLevelDynamicResourceDocsObp,
      implementedInApiVersion,
      nameOf(getBankLevelDynamicResourceDocsObp),
      "GET",
      "/banks/BANK_ID/resource-docs/API_VERSION/obp",
      "Get Bank Level Dynamic Resource Docs.",
      getResourceDocsDescription(true),
      EmptyBody,
      EmptyBody,
      UnknownError :: Nil,
      List(apiTagDocumentation, apiTagApi),
      Some(List(canReadDynamicResourceDocsAtOneBank))
    )

    // Provides resource documents so that API Explorer (or other apps) can display API documentation
    // Note: description uses html markup because original markdown doesn't easily support "_" and there are multiple versions of markdown.
    def getBankLevelDynamicResourceDocsObp : OBPEndpointFuture = {
      case "banks" :: bankId :: "resource-docs" :: requestedApiVersionString :: "obp" :: Nil JsonGet _ => {
        val (tags, partialFunctions, locale, contentParam, apiCollectionIdParam) = ResourceDocsAPIMethodsUtil.getParams()
        cc =>
          for {
            (u: Box[User], callContext: Option[CallContext]) <- resourceDocsRequireRole match {
              case false => anonymousAccess(cc)
              case true => authenticatedAccess(cc) // If set resource_docs_requires_role=true, we need check the authentication
            }
            _ <- if (locale.isDefined) {
              Helper.booleanToFuture(failMsg = s"$InvalidLocale Current Locale is ${locale.get}" intern(), cc = cc.callContext) {
                APIUtil.obpLocaleValidation(locale.get) == SILENCE_IS_GOLDEN
              }
            } else {
              Future.successful(true)
            }
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), Option(cc))
            _ <- resourceDocsRequireRole match {
              case false => Future()
              case true => // If set resource_docs_requires_role=true, we need check the the roles as well
                NewStyle.function.hasAtLeastOneEntitlement(failMsg = UserHasMissingRoles + ApiRole.canReadDynamicResourceDocsAtOneBank.toString)(
                  bankId, u.map(_.userId).getOrElse(""), ApiRole.canReadDynamicResourceDocsAtOneBank::Nil, cc.callContext
                )
            }
            requestedApiVersion <- NewStyle.function.tryons(s"$InvalidApiVersionString $requestedApiVersionString", 400, callContext) {ApiVersionUtils.valueOf(requestedApiVersionString)}
            cacheKey = APIUtil.createResourceDocCacheKey(
              Some(bankId),
              requestedApiVersionString,
              tags,
              partialFunctions,
              locale,
              contentParam,
              apiCollectionIdParam,
              None)
            json <- NewStyle.function.tryons(s"$UnknownError Can not create dynamic resource docs.", 400, callContext) {
              val cacheValueFromRedis = Caching.getDynamicResourceDocCache(cacheKey)
              if (cacheValueFromRedis.isDefined) {
                json.parse(cacheValueFromRedis.get)
              } else {
                val resourceDocJson = getResourceDocsObpDynamicCached(tags, partialFunctions, locale, None, false)
                val resourceDocJsonJValue = resourceDocJson.map(resourceDocsJsonToJsonResponse).head
                val jsonString = json.compactRender(resourceDocJsonJValue)
                Caching.setDynamicResourceDocCache(cacheKey, jsonString)
                resourceDocJsonJValue
              }
            }
          } yield {
            (Full(json), HttpCode.`200`(callContext))
          }
      }
    }


    localResourceDocs += ResourceDoc(
      getResourceDocsSwagger,
      implementedInApiVersion,
      "getResourceDocsSwagger",
      "GET",
      "/resource-docs/API_VERSION/swagger",
      "Get Swagger documentation",
      s"""Returns documentation about the RESTful resources on this server in Swagger format.
         |
         |API_VERSION is the version you want documentation about e.g. v3.0.0
         |
         |You may filter this endpoint using the 'tags' url parameter e.g. ?tags=Account,Bank
         |
         |(All endpoints are given one or more tags which for used in grouping)
         |
         |You may filter this endpoint using the 'functions' url parameter e.g. ?functions=getBanks,bankById
         |
         |(Each endpoint is implemented in the OBP Scala code by a 'function')
         |
         |See the Resource Doc endpoint for more information.
         |
         | Note: Resource Docs are cached, TTL is ${GET_DYNAMIC_RESOURCE_DOCS_TTL} seconds
         | 
         |Following are more examples:
         |${getObpApiRoot}/v3.1.0/resource-docs/v3.1.0/swagger
         |${getObpApiRoot}/v3.1.0/resource-docs/v3.1.0/swagger?tags=Account,Bank
         |${getObpApiRoot}/v3.1.0/resource-docs/v3.1.0/swagger?functions=getBanks,bankById
         |${getObpApiRoot}/v3.1.0/resource-docs/v3.1.0/swagger?tags=Account,Bank,PSD2&functions=getBanks,bankById
         |
      """,
      EmptyBody,
      EmptyBody,
      UnknownError :: Nil,
      List(apiTagDocumentation, apiTagApi)
    )


    def getResourceDocsSwagger : OBPEndpointFuture = {
      case "resource-docs" :: requestedApiVersionString :: "swagger" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          val (resourceDocTags, partialFunctions, locale, contentParam,  apiCollectionIdParam) = ResourceDocsAPIMethodsUtil.getParams()
          for {
            requestedApiVersion <- NewStyle.function.tryons(s"$InvalidApiVersionString Current Version is $requestedApiVersionString", 400, cc.callContext) {
              ApiVersionUtils.valueOf(requestedApiVersionString)
            }
            _ <- Helper.booleanToFuture(failMsg = s"$ApiVersionNotSupported Current Version is $requestedApiVersionString", cc=cc.callContext) {
              versionIsAllowed(requestedApiVersion)
            }
            _ <- if (locale.isDefined) {
              Helper.booleanToFuture(failMsg = s"$InvalidLocale Current Locale is ${locale.get}" intern(), cc = cc.callContext) {
                APIUtil.obpLocaleValidation(locale.get) == SILENCE_IS_GOLDEN
              }
            } else {
              Future.successful(true)
            }
            isVersion4OrHigher = true
            cacheKey = APIUtil.createResourceDocCacheKey(
              None,
              requestedApiVersionString,
              resourceDocTags,
              partialFunctions,
              locale,
              contentParam,
              apiCollectionIdParam,
              Some(isVersion4OrHigher)
            )
            cacheValueFromRedis = Caching.getStaticSwaggerDocCache(cacheKey)
            
            swaggerJValue <- if (cacheValueFromRedis.isDefined) {
              NewStyle.function.tryons(s"$UnknownError Can not convert internal swagger file from cache.", 400, cc.callContext) {json.parse(cacheValueFromRedis.get)}
            } else {
              NewStyle.function.tryons(s"$UnknownError Can not convert internal swagger file.", 400, cc.callContext) {
                val resourceDocsJsonFiltered = locale match {
                  case _ if (apiCollectionIdParam.isDefined) =>
                    val operationIds = MappedApiCollectionEndpointsProvider.getApiCollectionEndpoints(apiCollectionIdParam.getOrElse("")).map(_.operationId).map(getObpFormatOperationId)
                    val resourceDocs = ResourceDoc.getResourceDocs(operationIds)
                    val resourceDocsJson = JSONFactory1_4_0.createResourceDocsJson(resourceDocs, isVersion4OrHigher, locale)
                    resourceDocsJson.resource_docs
                  case _ =>
                    contentParam match {
                      case Some(DYNAMIC) =>
                        getResourceDocsObpDynamicCached(resourceDocTags, partialFunctions, locale, None, isVersion4OrHigher).head.resource_docs
                      case Some(STATIC) => {
                        getStaticResourceDocsObpCached(requestedApiVersionString, resourceDocTags, partialFunctions, locale, isVersion4OrHigher).head.resource_docs
                      }
                      case _ => {
                        getAllResourceDocsObpCached(requestedApiVersionString, resourceDocTags, partialFunctions, locale, contentParam, isVersion4OrHigher).head.resource_docs
                      }
                    }
                }
                convertResourceDocsToSwaggerJvalueAndSetCache(cacheKey, requestedApiVersionString, resourceDocsJsonFiltered)
              }
            }
          } yield {
            (swaggerJValue, HttpCode.`200`(cc.callContext))
          }
        }
      }
    }

  }
}
