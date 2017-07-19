package pl.ekodo.play.auth0

import com.typesafe.config.Config

case class Auth0Configuration(
  auth0domain: String,
  issuer: String,
  audience: String
)

object Auth0Configuration {

  def apply(config: Config): Auth0Configuration = Auth0Configuration(
    config.getString("auth0domain"),
    config.getString("issuer"),
    config.getString("audience")
  )

}
