package bootstrap.http4s

import cats.effect._
import org.http4s.{HttpRoutes, _}
import org.http4s.dsl.io._
import cats.implicits._
import code.api.util.{APIUtil, CustomJsonFormats}
import code.bankconnectors.Connector
import code.model.dataAccess.MappedBank
import com.openbankproject.commons.model.BankCommons
import net.liftweb.json.Formats
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import scala.language.higherKinds
import cats.effect._
import code.api.v4_0_0.JSONFactory400
import org.http4s._
import org.http4s.dsl.io._
import net.liftweb.json.JsonAST.{JValue, prettyRender}
import net.liftweb.json.{Extraction, MappingException, compactRender, parse}
//import org.http4s.server.middleware.ErrorHandling
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import cats.data.{OptionT,Kleisli }
import cats.effect.IO


/**
 * this to test the error handing. try to make the proper error response
 */
object RestRoutes {
  implicit val formats: Formats = CustomJsonFormats.formats

  case class ErrorResponse(message: String)
  
  val helloWorldService: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      if(name=="error"){
        BadRequest(prettyRender(Extraction.decompose(ErrorResponse("This is the error!!!"))))
      }else if(name=="error2"){
        IO.raiseError(new RuntimeException("Something went wrong!"))
          .handleErrorWith { error =>
            InternalServerError( prettyRender(Extraction.decompose(ErrorResponse(error.getMessage))))
          }
      }else if(name=="exception"){
        throw new Exception("Hey I am the exception !")
      }else{
        Ok(s"Hello, $name.")
      }  
  }

  val bankServices: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "banks"  =>
      val banks = Connector.connector.vend.getBanksLegacy(None).map(_._1).openOrThrowException("xxxxx")
      Ok(prettyRender(Extraction.decompose(banks)))
    case GET -> Root / "banks"/ "future"  =>
      import scala.concurrent.Future
      import scala.concurrent.ExecutionContext.Implicits.global
      Ok(IO.fromFuture(IO(
        for {
          (banks, callContext) <- code.api.util.NewStyle.function.getBanks(None)
        } yield {
          prettyRender(Extraction.decompose(JSONFactory400.createBanksJson(banks)))
        }
      )))
      
      val banks = Connector.connector.vend.getBanksLegacy(None).map(_._1).openOrThrowException("xxxxx")
      Ok(prettyRender(Extraction.decompose(banks)))
    case GET -> Root / "banks" / IntVar(bankId) =>
      val bank = BankCommons(
        bankId = com.openbankproject.commons.model.BankId("bankIdExample.value"),
        shortName = "bankShortNameExample.value",
        fullName = "bankFullNameExample.value",
        logoUrl = "bankLogoUrlExample.value",
        websiteUrl = "bankWebsiteUrlExample.value",
        bankRoutingScheme = "bankRoutingSchemeExample.value",
        bankRoutingAddress = "bankRoutingAddressExample.value",
        swiftBic = "bankSwiftBicExample.value",
        nationalIdentifier = "bankNationalIdentifierExample.value")
      Ok(prettyRender(Extraction.decompose(bank)))
  }
  
}