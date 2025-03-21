package bootstrap.http4s

import bootstrap.http4s.RestRoutes.{bankServices, helloWorldService}
import cats.data.{Kleisli, OptionT}

import scala.language.higherKinds
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s.ember.server._
import org.http4s.implicits._
import cats.effect._
import org.http4s._
import code.api.Constant._
import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.HttpRoutes
import org.http4s.implicits._
import com.comcast.ip4s.{Host, Port}
import java.net.URI
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import java.io.FileInputStream
import java.security.Security
import fs2.io.net.tls.TLSContext
import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.HttpRoutes
import org.http4s.implicits._
import com.comcast.ip4s.{Host, Port}
import java.net.URI
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import java.io.FileInputStream
import fs2.io.net.tls.TLSContext
import cats.effect.kernel.Async
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.ErrorHandling
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import net.liftweb.json.JsonAST.{JValue, prettyRender}
import net.liftweb.json.{Extraction, MappingException, compactRender, parse}
import code.api.util.{APIUtil, CustomJsonFormats}
import code.util.Helper.MdcLoggable
import fs2.text.utf8
import org.http4s.dsl.io._

object Http4sServer extends IOApp with MdcLoggable {
  implicit val formats = CustomJsonFormats.formats
  
  //this is the routers
  val services: Kleisli[({type λ[β$0$] = OptionT[IO, β$0$]})#λ, Request[IO], Response[IO]] =
    code.api.v1_3_0.Http4s130.wrappedRoutesV130Services <+>
      bankServices <+>
      helloWorldService

  case class ErrorResponse(message: String)
  
  def errorHandler(t: Throwable, msg: => String): OptionT[IO, Response[IO]] = {
    val errorResponse = ErrorResponse(t.getMessage)
    val jsonResponse = prettyRender(Extraction.decompose(errorResponse))

    val logAction = IO(logger.error(s"Error occurred: ${jsonResponse}", t))

    // TODO. this need to check
    OptionT.liftF(logAction *> BadRequest(jsonResponse))
  }


  val withErrorHandling = ErrorHandling.Recover.total(
    ErrorAction.log(
      services,
      messageFailureLogAction = (t, msg) => errorHandler(t, msg).void,
      serviceErrorLogAction = (t, msg) => errorHandler(t, msg).void
    )
  )

  def logResponses(service: HttpRoutes[IO]): HttpRoutes[IO] = HttpRoutes { req =>
    service(req).flatMap { response =>
      if (response.status.code >= 400) {
        val bodyAsStringIO = response.body.through(utf8.decode).compile.string

        for {
          bodyAsString <- OptionT.liftF(bodyAsStringIO)
          _ <- OptionT.liftF(IO(logger.error(s"HTTP ${response.status.code} - ${response.status.reason}\nBody: $bodyAsString")))
          result <- OptionT.pure[IO](response)
        } yield result
        
      } else {
        OptionT.pure[IO](response)
      }
    }
  }

  val withLogging = logResponses(withErrorHandling)
  val httpApp: Kleisli[IO, Request[IO], Response[IO]] = withLogging.orNotFound

  
  //Start OBP relevant objects, and settings
  new bootstrap.liftweb.Boot().boot

  // Define the host and port as variables
  val host: Host = Host.fromString(HostName).head
  val port: Option[Port] = DevPort.map(Port.fromInt(_)).toOption.flatten

  // Convert SSLContext to TLSContext
  private def toTLSContext(sslContext: SSLContext): IO[TLSContext[IO]] = {
    IO(TLSContext.Builder.forAsync[IO](Async[IO]).fromSSLContext(sslContext))
  }

  // Load the keystore and create an SSLContext (optional) //TODO this should be a helper function
  private def createSSLContext: IO[Option[SSLContext]] = IO {
    // Path to the keystore file
    val keystorePath = "path/to/keystore.jks"
    // Keystore password
    val keystorePassword = "changeit".toCharArray

    // Load the keystore
    val keystore = KeyStore.getInstance("JKS")
    val keystoreStream = new FileInputStream(keystorePath)
    keystore.load(keystoreStream, keystorePassword)
    keystoreStream.close()

    // Initialize KeyManagerFactory
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(keystore, keystorePassword)

    // Initialize TrustManagerFactory
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(keystore)

    // Create and initialize the SSLContext
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, null)
    Some(sslContext)
  }.handleErrorWith { _ =>
    IO.pure(None) // If keystore loading fails, return None (HTTPS disabled)
  }

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      sslContextOpt <- createSSLContext // Create the SSLContext (optional)
      tlsContextOpt <- sslContextOpt match {
        case Some(sslContext) => toTLSContext(sslContext).map(Some(_)) // Convert to TLSContext
        case None => IO.pure(None) // No TLSContext if SSLContext is not available
      }
      exitCode <- {
        // Start with the default EmberServerBuilder
        val serverBuilder = EmberServerBuilder
          .default[IO]
          .withHost(host) // Use the extracted hostname

        // Conditionally add the port if it is provided
        val serverBuilderWithPort = port match {
          case Some(p) => serverBuilder.withPort(p)
          case None => serverBuilder
        }

        // Conditionally enable HTTPS if TLSContext is available
        val serverBuilderWithTLS = tlsContextOpt match {
          case Some(tlsContext) => serverBuilderWithPort.withTLS(tlsContext) // Enable HTTPS
          case None => serverBuilderWithPort // Use HTTP
        }

        // Build and start the server
        serverBuilderWithTLS
          .withHttpApp(httpApp)
          .build
          .use(_ => IO.never) // Keep the server running indefinitely
          .as(ExitCode.Success)
      }
    } yield exitCode
  }
}

//this is testing code
object myApp extends App{
  import cats.effect.unsafe.implicits.global
  Http4sServer.run(Nil).unsafeRunSync()
//  Http4sServer.run(Nil).unsafeToFuture()//.unsafeRunSync()
}

