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
object Http4sServer extends IOApp {

  val services: Kleisli[({type λ[β$0$] = OptionT[IO, β$0$]})#λ, Request[IO], Response[IO]] =  
    bankServices <+> 
      helloWorldService //<+>
//      code.api.v1_3_0.Http4s130.wrappedRoutesV130Services
      
  val httpApp: Kleisli[IO, Request[IO], Response[IO]] = (services).orNotFound

  //Start OBP relevant objects, and settings
  new bootstrap.liftweb.Boot().boot
  
  override def run(args: List[String]): IO[ExitCode] = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8081")
    .withHttpApp(httpApp)
    .build
    .use(_ => IO.never)
    .as(ExitCode.Success)
}

//this is testing code
object myApp extends App{
  import cats.effect.unsafe.implicits.global
  Http4sServer.run(Nil).unsafeRunSync()
//  Http4sServer.run(Nil).unsafeToFuture()//.unsafeRunSync()
}

