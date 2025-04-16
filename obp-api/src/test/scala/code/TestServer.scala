package code

import bootstrap.http4s.Http4sServer
import code.api.util.APIUtil
import org.http4s.ember.server.EmberServerBuilder
import cats.effect._

import java.util.UUID
import com.comcast.ip4s.{Host, Port}
import cats.effect.unsafe.implicits.global

object TestServer {

  val host = "localhost"
  val port = APIUtil.getPropsAsIntValue("tests.port",8000)
  val externalHost = APIUtil.getPropsValue("external.hostname")
  val externalPort = APIUtil.getPropsAsIntValue("external.port")

  new bootstrap.liftweb.Boot().boot
  
//  val server = EmberServerBuilder
//    .default[IO]
//    .withHost(host) // Use the extracted hostname
//    .withPort(port)
//    .withHttpApp(bootstrap.http4s.Http4sServer.httpApp)
//    .build
//    .use(_ => IO.never) // Keep the server running indefinitely
//    .as(ExitCode.Success)
//
//  server.start().unsafeRunSync()

  // Blocking startup for test stability

//  val server = EmberServerBuilder
//    .default[IO]
//    .withHost(Host.fromString(host).get)
//    .withPort(Port.fromInt(port).get)
//    .withHttpApp(bootstrap.http4s.Http4sServer.httpApp)
//    .build
//    .use { _ =>
//      IO.println("HTTP4S server initialized and running (blocking for tests)...") *>
//        IO.never // block forever
//    }
//    .unsafeRunSync() // <--- BLOCK here until server starts
  

  @volatile var cancelToken: Option[IO[Unit]] = None

  def startServer(): Unit = {
    val serverResource = EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(host).get)
      .withPort(Port.fromInt(port).get)
      .withHttpApp(Http4sServer.httpApp)
      .build

    val program = serverResource.allocated.flatMap {
      case (server, shutdown) =>
        IO(println(s"Server started on ${server.address}")) *>
          IO {
            cancelToken = Some(shutdown)
          } *> IO.never
    }

    // Fire-and-forget, run in background
    program.start.unsafeRunSync()
  }

  def stopServer(): Unit = {
    println("[TestServer] Shutting down server...")
    cancelToken.foreach(_.unsafeRunSync())
  }
  
  val userId1 = Some(UUID.randomUUID.toString)
  val userId2 = Some(UUID.randomUUID.toString)
  val userId3 = Some(UUID.randomUUID.toString)
  val userId4 = Some(UUID.randomUUID.toString)

  val resourceUser1Name = "resourceUser1"
  val resourceUser2Name = "resourceUser2"
  val resourceUser3Name = "resourceUser3"
  val resourceUser4Name = "resourceUser4"

}

