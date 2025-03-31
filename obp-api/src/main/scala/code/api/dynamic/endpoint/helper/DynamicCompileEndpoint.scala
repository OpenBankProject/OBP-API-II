package code.api.dynamic.endpoint.helper

import code.api.util.APIUtil.{OBPEndpointFuture, OBPReturnType, futureToBoxedResponse, scalaFutureToLaFuture}
import code.api.util.DynamicUtil.{Sandbox, Validation}
import code.api.util.{CallContext, CustomJsonFormats, DynamicUtil}
import net.liftweb.common.Box
import net.liftweb.http.{JsonResponse, Req}
import scala.concurrent.{Future, Promise}
/**
 * this is super trait of dynamic compile endpoint, the dynamic compiled code should extends this trait and supply
 * logic of process method
 */
trait DynamicCompileEndpoint {
  implicit val formats = CustomJsonFormats.formats
  import com.openbankproject.commons.ExecutionContext.Implicits.global
  // * is any bankId
  val boundBankId: String

  protected def process(callContext: CallContext, request: Req, pathParams: Map[String, String]): Box[JsonResponse]

  val endpoint: OBPEndpointFuture = new OBPEndpointFuture {
    override def isDefinedAt(x: Req): Boolean = true

    override def apply(request: Req): CallContext => Future[(Box[JsonResponse], Option[CallContext])] = { cc =>
      val Some(pathParams) = cc.resourceDocument.map(_.getPathParams(request.path.partPath))

      validateDependencies()

      val result: Box[JsonResponse] = Sandbox.sandbox(boundBankId).runInSandbox {
        process(cc, request, pathParams)
      }
      Future{(result, Some(cc))}
    }
  }

  private def validateDependencies() = {
    val dependencies = DynamicUtil.getDynamicCodeDependentMethods(this.getClass, "process" == )
    Validation.validateDependency(dependencies)
  }
}

object DynamicCompileEndpoint {
   implicit def scalaFutureToBoxedJsonResponse[T](scf: OBPReturnType[T])(implicit m: Manifest[T]): Box[JsonResponse] = {
    futureToBoxedResponse(scalaFutureToLaFuture(scf))
  }
}