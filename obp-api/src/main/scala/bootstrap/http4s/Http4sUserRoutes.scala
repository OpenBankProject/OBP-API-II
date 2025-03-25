package code.api.http4s

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import code.api.util.CallContext
import code.api.http4s.AuthMiddleware._
import code.model.dataAccess.AuthUser
import net.liftweb.json.Serialization
import code.api.util.CustomJsonFormats
import code.api.http4s.CallContextKeyProvider.callContextKey
import com.openbankproject.commons.model.User

object Http4sUserRoutes {

  implicit val formats = CustomJsonFormats.formats

  val userRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "me" =>
      securedEndpoint { (user: User, ctx: CallContext) =>
        val userInfo = UserInfo(user)
        val jsonStr = Serialization.write(userInfo)
        Ok(jsonStr).map(_.withContentType(`Content-Type`(MediaType.application.json)))
      }(req)
  }

  case class UserInfo(userId: String, name: String, email: String)

  object UserInfo {
    def apply(user: User): UserInfo =
      UserInfo(user.userId, user.name, user.emailAddress)
  }
}
