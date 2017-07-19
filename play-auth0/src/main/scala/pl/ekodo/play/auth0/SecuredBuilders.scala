package pl.ekodo.play.auth0

import java.math.BigInteger
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.security.{ KeyFactory, PublicKey }
import javax.inject.{ Inject, Singleton }

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.github.blemale.scaffeine.{ AsyncLoadingCache, Scaffeine }
import org.apache.commons.codec.binary.Base64
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import scalacache._
import scalacache.caffeine._
import scalacache.memoization.memoize

object JWKSet {

  val rsaJwkReads: Reads[RSAjwk] = (
    (JsPath \ "kid").read[String] and
    (JsPath \ "alg").read[String] and
    (JsPath \ "n").read[String] and
    (JsPath \ "e").read[String]
  ) (RSAjwk.apply _)

  implicit val jwkReads: Reads[JWK] = new Reads[JWK] {
    override def reads(json: JsValue): JsResult[JWK] = json match {
      case JsObject(fields) =>
        fields.get("kty") match {
          case Some(JsString(kty)) if kty == "RSA" => rsaJwkReads.reads(json)
          case Some(JsString(kty)) => JsError(s"Unsupported Key Type: $kty")
          case _ => JsError("Missing Key Type (kty)")
        }
      case _ =>
        JsError(s"Expected object, got ${Json.prettyPrint(json)}")
    }
  }

  implicit val jwkSetReads: Reads[JWKSet] = (JsPath \ "keys").read[List[JWK]].map(k => JWKSet(k))
}

case class JWKSet(keys: List[JWK])

sealed trait JWK {
  def kid: String

  def alg: String
}

object RSAjwk {
  val kty = "RSA"
}

final case class RSAjwk(
    kid: String,
    alg: String,
    n: String,
    e: String
) extends JWK {
  def publicKey: PublicKey = {
    val kf = KeyFactory.getInstance(RSAjwk.kty)
    val modulus = new BigInteger(1, Base64.decodeBase64(n))
    val exponent = new BigInteger(1, Base64.decodeBase64(e))
    kf.generatePublic(new RSAPublicKeySpec(modulus, exponent))
  }
}

object SecuredBuilders extends App {

}

class VerifiedRequest[A](val subject: String, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class SecuredBuilders @Inject() (ws: WSClient, configProvider: Auth0ConfigurationProvider)(implicit context: ExecutionContext) {

  implicit val scalaCache = ScalaCache(CaffeineCache())

  private val authPrefix = "Bearer "

  private val config = configProvider.get()

  private val jwks = config.auth0domain + "/.well-known/jwks.json"

  private def service: Future[Map[String, JWK]] = memoize(2.seconds) {
    ws.url(jwks).withHeaders("Accept" -> "application/json")
      .withRequestTimeout(3.seconds).get().map {
        _.json.validate[JWKSet].map { jwkSet =>
          jwkSet.keys.map(jwk => (jwk.kid, jwk)).toMap
        }.getOrElse(Map.empty)
      }
  }

  private val cache: AsyncLoadingCache[String, JWK] =
    Scaffeine()
      .expireAfterWrite(1.hour)
      .maximumSize(10)
      .buildAsyncFuture((kid: String) => service.flatMap {
        _.get(kid).map(Future.successful).getOrElse(Future.failed(new RuntimeException))
      })

  object Secured extends ActionBuilder[VerifiedRequest] with ActionRefiner[Request, VerifiedRequest] {
    override def refine[A](input: Request[A]): Future[Either[mvc.Results.Status, VerifiedRequest[A]]] =
      input.headers.get("Authorization").map { auth =>
        val token = auth.replaceFirst(authPrefix, "")
        Try {
          val decoded = JWT.decode(token)
          val jwkF = cache.get(decoded.getKeyId)
          jwkF.map {
            case jwk: RSAjwk =>
              val validator = JWT.require(Algorithm.RSA256(jwk.publicKey.asInstanceOf[RSAPublicKey], null))
                .withIssuer(config.issuer)
                .withAudience(config.audience)
                .build()
              val decoded = validator.verify(token)

              Right(new VerifiedRequest(decoded.getSubject, input))
          }
        }.getOrElse(Future.successful(Left(Results.Forbidden)))
      }.getOrElse(Future.successful(Left(Results.Forbidden)))
  }

}
