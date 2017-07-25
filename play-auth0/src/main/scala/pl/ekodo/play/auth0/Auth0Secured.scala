package pl.ekodo.play.auth0

import java.security.interfaces.RSAPublicKey
import javax.inject.{ Inject, Singleton }

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.github.blemale.scaffeine.{ AsyncLoadingCache, Scaffeine }
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import scalacache._
import scalacache.caffeine._
import scalacache.memoization.memoize

class VerifiedRequest[A](val subject: String, request: Request[A]) extends WrappedRequest[A](request)

object Auth0Secured {

  private val authPrefix = "Bearer "

  private val jwksPath = "/.well-known/jwks.json"

}

case object ValidationException extends RuntimeException

@Singleton
class Auth0Secured @Inject() (ws: WSClient, configProvider: Auth0ConfigurationProvider)(implicit context: ExecutionContext) {

  private implicit val scalaCache = ScalaCache(CaffeineCache())

  private val config = configProvider.get()

  private val jwks = config.auth0domain + Auth0Secured.jwksPath

  def validate(header: String): Future[String] = {
    if (header.startsWith(Auth0Secured.authPrefix)) {
      val token = header.stripPrefix(Auth0Secured.authPrefix)
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
            decoded.getSubject
        }
      }.getOrElse(Future.failed(ValidationException))
    } else Future.failed(ValidationException)
  }

  private def service: Future[Map[String, JWK]] = memoize(config.jwks.serviceMemoize) {
    ws.url(jwks).addHttpHeaders("Accept" -> "application/json")
      .withRequestTimeout(3.seconds).get().map {
        _.json.validate[JWKSet].map { jwkSet =>
          jwkSet.keys.map(jwk => (jwk.kid, jwk)).toMap
        }.getOrElse(Map.empty)
      }
  }

  private val cache: AsyncLoadingCache[String, JWK] =
    Scaffeine()
      .expireAfterWrite(config.jwks.cacheMaxAge)
      .maximumSize(config.jwks.cacheMaxEntries)
      .buildAsyncFuture((kid: String) => service.flatMap {
        _.get(kid).map(Future.successful).getOrElse(Future.failed(new RuntimeException))
      })

}
