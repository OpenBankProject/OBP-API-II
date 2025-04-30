package bootstrap.http4s

import bootstrap.http4s.Http4s130.v130Services
import bootstrap.http4s.middleware.CallContextMiddleware.withCallContext
import bootstrap.http4s.middleware.JsonErrorHandlerMiddleware
import bootstrap.http4s.middleware.ResponseMiddleware.contentTypeMiddleware
import bootstrap.http4s.test.RestRoutes.{bankServices, helloWorldService}
import cats.data.{Kleisli, OptionT}
import cats.effect.kernel.Async
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import code.api.Constant._
import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable
import com.comcast.ip4s.{Host, Port}
import fs2.io.net.tls.TLSContext
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._

import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.language.higherKinds

object Http4sServer extends IOApp with MdcLoggable {
  implicit val formats = CustomJsonFormats.formats


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
    EmberClientBuilder.default[IO].build.use { client =>
      val obpApiBaseUri = uri"http://localhost:8080"
      val obpApiDispatch = new ObpApiDispatch(client, obpApiBaseUri).routes

      //this is the routers
      val services: Kleisli[({type λ[β$0$] = OptionT[IO, β$0$]})#λ, Request[IO], Response[IO]] = contentTypeMiddleware(JsonErrorHandlerMiddleware(withCallContext(
        code.api.v1_3_0.APIMethods130.Implementations1_3_0.allRoutes <+>
          v130Services <+>
          bankServices <+>
          helloWorldService <+>
          obpApiDispatch //just pass though any request to the obp api and get the response
      )))

      val httpApp: Kleisli[IO, Request[IO], Response[IO]] = (services).orNotFound

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
}

//this is testing code
object myApp extends App{
  import cats.effect.unsafe.implicits.global
  Http4sServer.run(Nil).unsafeRunSync()
//  Http4sServer.run(Nil).unsafeToFuture()//.unsafeRunSync()
}

