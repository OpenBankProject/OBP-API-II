package code.api.ResourceDocs1_4_0

import cats.effect._
import cats.syntax.all._
import code.api.Constant.ApiPathZero
import code.api.ResourceDocs1_4_0.ResourceDocsAPIMethodsUtil._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ApiVersion
// JObject creation
//import code.api.v1_3_0.{APIMethods130, OBPAPI1_3_0}
import scala.collection.mutable.ArrayBuffer

// So we can include resource docs from future versions
import com.openbankproject.commons.ExecutionContext.Implicits.global


object ResourceDocsAPIMethods extends MdcLoggable {

  val ImplementationsResourceDocs = new Object() {

    val localResourceDocs = ArrayBuffer[ResourceDoc]()

    val implementedInApiVersion = ApiVersion.v1_4_0

    implicit val formats = CustomJsonFormats.rolesMappedToClassesFormats

//    localResourceDocs += ResourceDoc(
//      getResourceDocsObpV400,
//      implementedInApiVersion,
//      nameOf(getResourceDocsObpV400),
//      "GET",
//      "/resource-docs/API_VERSION/obp",
//      "Get Resource Docs",
//      getResourceDocsDescription(false),
//      EmptyBody,
//      EmptyBody,
//      UnknownError :: Nil,
//      List(apiTagDocumentation, apiTagApi),
//      Some(List(canReadResourceDoc))
//    )

    import org.http4s.HttpRoutes
    import org.http4s.dsl.io._

    // Common prefix: /obp/v1.3.0
    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // Http4s route for resource-docs endpoint
    val getResourceDocsObpV400Route: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req@GET -> `prefixPath` / "resource-docs" / requestedApiVersionString / "obp" =>
        // Extract query parameters from the request
        val logic = for {
          httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
          // Extract specific parameters
          tagsParam = httpParams.filter(_.name == "tags").map(_.values).headOption
          functionsParam = httpParams.filter(_.name == "functions").map(_.values).headOption
          localeParam = httpParams.filter(param => param.name == "locale" || param.name == "language").map(_.values).flatten.headOption
          contentParam = httpParams.filter(_.name == "content").map(_.values).flatten.flatMap(ResourceDocsAPIMethodsUtil.stringToContentParam).headOption
          apiCollectionIdParam = httpParams.filter(_.name == "api-collection-id").map(_.values).flatten.headOption
         
          tags = tagsParam.map(_.map(ResourceDocTag(_)))

          // Create a CallContext
          callContext = CallContext()

          // Call the existing method
          result <- getApiLevelResourceDocs(callContext, requestedApiVersionString, tags, functionsParam, localeParam, contentParam, apiCollectionIdParam, true)
          jsonResult = result._1.map(_.json.toJsCmd).head
        } yield jsonResult

        // Return the result
        IO.fromFuture(IO(logic)).flatMap(result => Ok(result))
    }

    // All routes combined
    val allRoutes: HttpRoutes[IO] =
      getResourceDocsObpV400Route <+>
        getResourceDocsObpV400Route 
  }
}