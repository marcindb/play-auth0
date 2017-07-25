import sbt._

object Dependencies {

  object Versions {
    val crossScala = Seq("2.12.2", "2.11.11")
    val scalaVersion = crossScala.head
  }

  object Play {
    val version = "2.6.2"

    val core = "com.typesafe.play" %% "play" % version % Provided
    val ws = "com.typesafe.play" %% "play-ws" % version % Provided
    val cache = "com.typesafe.play" %% "play-cache" % version % Provided

    val testPlus = "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.1" % Test
    val playSpecs2 = "com.typesafe.play" %% "play-specs2" % version % Test

  }

  val scaffeine = "com.github.blemale" %% "scaffeine" % "2.1.0"

  val scalaCache = "com.github.cb372" %% "scalacache-caffeine" % "0.9.4"

  val javaJwt = "com.auth0" % "java-jwt" % "3.2.0"

  val guice = "com.google.inject" % "guice" % "4.0"

}
