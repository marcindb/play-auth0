import com.typesafe.sbt.SbtScalariform._
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import interplay.ScalaVersions

import scalariform.formatter.preferences._

lazy val commonSettings = SbtScalariform.scalariformSettings ++ Seq(
  // scalaVersion needs to be kept in sync with travis-ci
  scalaVersion := ScalaVersions.scala211,
  crossScalaVersions := Seq(ScalaVersions.scala211),
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(SpacesAroundMultiImports, true)
    .setPreference(SpaceInsideParentheses, false)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(DoubleIndentClassDeclaration, true)
)

// needs to be kept in sync with travis-ci
val PlayVersion = playVersion(sys.env.getOrElse("PLAY_VERSION", "2.5.15"))

lazy val `play-auth0` = (project in file("play-auth0"))
  .enablePlugins(PlayLibrary)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "javax.inject" % "javax.inject" % "1",
      "com.typesafe" % "config" % "1.3.1",
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "com.github.blemale" %% "scaffeine" % "2.1.0",
      "com.github.cb372" %% "scalacache-caffeine" % "0.9.4",
      "com.auth0" % "java-jwt" % "3.2.0",
      "com.typesafe.play" %% "play" % PlayVersion,
      "com.typesafe.play" %% "play-ws" % PlayVersion,
      "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % Test
    )
  )

lazy val `play-auth0-guice` = (project in file("play-auth0-guice"))
  .enablePlugins(PlayLibrary)
  .settings(commonSettings)
  .dependsOn(`play-auth0`)
  .settings(
    libraryDependencies ++= Seq(
      "com.google.inject" % "guice" % "4.0", // 4.0 to maybe make it work with 2.5 and 2.6
      "com.typesafe.play" %% "play" % PlayVersion % Test,
      "com.typesafe.play" %% "play-specs2" % PlayVersion % Test
    )
  )

lazy val `play-auth0-root` = (project in file("."))
  .enablePlugins(PlayRootProject, PlayReleaseBase)
  .settings(commonSettings)
  .aggregate(`play-auth0`, `play-auth0-guice`)

playBuildRepoName in ThisBuild := "play-auth0"

mimaDefaultSettings
