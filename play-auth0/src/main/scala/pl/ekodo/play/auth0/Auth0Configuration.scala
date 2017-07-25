package pl.ekodo.play.auth0

import com.typesafe.config.Config

import scala.concurrent.duration.Duration

case class Auth0Configuration(
  auth0domain: String,
  issuer: String,
  audience: String,
  jwks: JwksConfiguration
)

case class JwksConfiguration(
  cacheMaxEntries: Int,
  cacheMaxAge: Duration,
  serviceMemoize: Duration
)

object Auth0Configuration {

  def apply(config: Config): Auth0Configuration = Auth0Configuration(
    config.getString("auth0domain"),
    config.getString("issuer"),
    config.getString("audience"),
    JwksConfiguration(
      config.getInt("jwks.cacheMaxEntries"),
      Duration.fromNanos(config.getDuration("jwks.cacheMaxAge").toNanos),
      Duration.fromNanos(config.getDuration("jwks.serviceMemoize").toNanos)
    )
  )

}
