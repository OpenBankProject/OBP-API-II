package bootstrap.http4s

import cats.effect.IO
import net.liftweb.http._
import net.liftweb.http.provider._
import net.liftweb.common._
import net.liftweb.util._
import net.liftweb.json._
import org.http4s._
import scala.xml.NodeSeq
import java.io.ByteArrayInputStream
import net.liftweb.http.{S, Req, LiftRules}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.collection.mutable
import net.liftweb.common.{Box, Full}
import net.liftweb.http.{S, Req, LiftRules}
import net.liftweb.http.provider.HTTPRequest
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import net.liftweb.http.provider.HTTPRequest

import net.liftweb.http._
import net.liftweb.http.provider._
import net.liftweb.common._
import net.liftweb.util._
import net.liftweb.json._
import org.http4s._
import scala.xml.NodeSeq
import java.io.ByteArrayInputStream

import net.liftweb.http.provider._
import net.liftweb.common._
import net.liftweb.http._
import java.io.{ByteArrayInputStream, InputStream}
import scala.xml.NodeSeq
import java.util.Locale


object LiftCompatUtils {

  

  class MockLiftHttpRequest(
    val uri: String,
    val url: String,
    val bodyBytes: Array[Byte],
    val headerList: List[HTTPParam],
    val paramList: List[HTTPParam],
    val cookieList: List[HTTPCookie]
  ) extends HTTPRequest {

    override def param(name: String): List[String] =
      paramList.filter(_.name == name).flatMap(_.values)

    override def params: List[HTTPParam] = paramList

    override def cookies: List[HTTPCookie] = cookieList

    override def headers(name: String): List[String] =
      headerList.filter(_.name.equalsIgnoreCase(name)).flatMap(_.values)

    override def contextPath: String = ""

    override def remoteAddress: String = "127.0.0.1"

    override def session: HTTPSession = new HTTPSession {
      private val attributes = scala.collection.mutable.Map.empty[String, Any]

      def sessionId: String = "dummy"
      def destroySession(): Unit = ()
      def snapshot: Box[LiftSession] = Empty
      def lastAccessed: Long = System.currentTimeMillis
      def maxInactiveInterval: Long = 3600

      def attribute(name: String): Any = attributes.getOrElse(name, null)
      def lastAccessedTime: Long = System.currentTimeMillis
      def link(liftSession: LiftSession): Unit = ()
      def removeAttribute(name: String): Unit = attributes.remove(name)
      def setAttribute(name: String, value: Any): Unit = attributes.update(name, value)
      def setMaxInactiveInterval(interval: Long): Unit = ()
      def terminate: Unit = ()
      def unlink(liftSession: LiftSession): Unit = ()
    }

    override def inputStream: InputStream = new ByteArrayInputStream(bodyBytes)

    override def multipartContent_? : Boolean = false

    override def contentType: Box[String] = headers("Content-Type").headOption

    override def suspend(timeout: Long): RetryState.Value = RetryState.TIMED_OUT

    // Unused or stubbed for compatibility
    def authType: Box[String] = Empty
    def context: HTTPContext = null
    def destroyServletSession(): Unit = ()
    def extractFiles: List[ParamHolder] = Nil
    def headers: List[HTTPParam] = headerList
    def locale: Box[Locale] = Empty
    def method: String = "GET"
    def paramNames: List[String] = paramList.map(_.name).distinct
    def provider: HTTPProvider = null
    def queryString: Box[String] = Empty
    def remoteHost: String = "localhost"
    def remotePort: Int = 8080
    def resume(what: (Req, LiftResponse)): Boolean = false
    def resumeInfo: Option[(Req, LiftResponse)] = None
    def scheme: String = "http"
    def serverName: String = "localhost"
    def serverPort: Int = 8080
    def sessionId: Box[String] = Full("dummy")
    def setCharacterEncoding(encoding: String): Unit = ()
    def snapshot: HTTPRequest = this
    def suspendResumeSupport_? : Boolean = false
    def userAgent: Box[String] = Empty
  }


  private def createLiftHttpRequest(http4sReq: Request[IO]): MockLiftHttpRequest = {
    import cats.effect.unsafe.implicits.global
    val bodyBytes: Array[Byte] =
      http4sReq.body.compile.toVector.unsafeRunSync().toArray.map(_.toByte)

    val headerList: List[HTTPParam] =
      http4sReq.headers.headers.map(h => HTTPParam(h.name.toString, h.value)).toList

    val paramList: List[HTTPParam] =
      http4sReq.multiParams.toList.flatMap {
        case (k, vs) => vs.map(v => HTTPParam(k, v))
      }

    val cookieList: List[HTTPCookie] =
      http4sReq.cookies.map { c =>
        new HTTPCookie(
          name = c.name,
          value = Full(c.content),                   // HTTPCookie requires a Box[String] for value
          domain = Empty,                            // Optional domain (set to Empty)
          path = Empty,                              // Optional path (set to Empty)
          maxAge = Empty,                            // Optional maxAge (set to Empty)
          version = Empty,                           // Optional version (set to Empty)
          secure_? = Full(false),                    // Secure flag from the cookie
          httpOnly = Full(false),                    // HTTP-only flag from the cookie
          sameSite = Empty                           // Optional SameSite policy (set to Empty)
        )
      }.toList

    new MockLiftHttpRequest(
      uri = http4sReq.uri.path.renderString,
      url = http4sReq.uri.renderString,
      bodyBytes = bodyBytes,
      headerList = headerList,
      paramList = paramList,
      cookieList = cookieList
    )
  }


  def createLiftRequestObject(http4sReq: Request[IO]): Req = {
    // Convert Http4s Request to MockLiftHttpRequest
    val liftHttpRequest = createLiftHttpRequest(http4sReq)

    val pathSegments = http4sReq.uri.path.segments.map(_.decoded()).toList

    val parsedPath = ParsePath(
      partPath = pathSegments,
      suffix = "",
      absolute = true,
      endSlash = false
    )

    val requestType = http4sReq.method match {
      case Method.GET             => GetRequest
      case Method.POST            => PostRequest
      case Method.PUT             => PutRequest
      case Method.DELETE          => DeleteRequest
      case Method.HEAD            => HeadRequest
      case Method.OPTIONS         => OptionsRequest
      case Method.PATCH           => PatchRequest
      case meth                   => UnknownRequest(meth.toString()) // http4s.RequestMethod support more methods
    }     
    
    val contentType = liftHttpRequest.contentType
    val nanoEnd = System.nanoTime()    // Timestamp for request end time

    // Custom default parameter calculator
    val paramCalculator: () => ParamCalcInfo = () => {
      val paramMap: Map[String, List[String]] = liftHttpRequest.params
        .groupBy(_.name)
        .map { case (k, vs: List[HTTPParam]) => k -> vs.flatMap(_.values) }

      val paramNames: List[String] = paramMap.keys.toList

      ParamCalcInfo(
        paramNames = paramNames,
        params = paramMap,
        uploadedFiles = Nil,
        body = Full(BodyOrInputStream(new ByteArrayInputStream(liftHttpRequest.bodyBytes)))
      )
    }



    val additionalParams = Map[String, String]() // No additional params for now

    // Use the correct constructor to create a Req object
    new Req(
      path = parsedPath,
      contextPath = "",
      requestType = requestType,
      contentType = contentType,
      request = liftHttpRequest,
      nanoStart = System.nanoTime(),
      nanoEnd = nanoEnd,
      _stateless_? = false,
      paramCalculator = paramCalculator,
      addlParams = Map.empty
    )

  }

}
