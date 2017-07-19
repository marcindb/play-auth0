package pl.ekodo.play.auth0

import javax.inject.{ Inject, Provider }

import com.typesafe.config.Config

class Auth0ConfigurationProvider @Inject() (config: Config) extends Provider[Auth0Configuration] {

  override def get(): Auth0Configuration = {
    Auth0Configuration(config.getConfig("auth0"))
  }

}
