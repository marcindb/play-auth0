package pl.ekodo.play.auth0

import play.api.{ Configuration, Environment }
import play.api.inject.Module

class PlayAuth0Module extends Module {
  def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[Auth0Configuration].toProvider[Auth0ConfigurationProvider]
  )
}
