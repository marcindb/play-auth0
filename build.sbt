import com.typesafe.sbt.SbtScalariform._
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings

import scalariform.formatter.preferences._

import Dependencies._

lazy val commonSettings = SbtScalariform.scalariformSettings ++ Seq(
  // scalaVersion needs to be kept in sync with travis-ci
  scalaVersion := "2.12.2",
  organization := "pl.ekodo",
  crossScalaVersions := Seq("2.11.11", scalaVersion.value),
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(SpacesAroundMultiImports, true)
    .setPreference(SpaceInsideParentheses, false)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(DoubleIndentClassDeclaration, true)
)


lazy val `play-auth0` = (project in file("play-auth0"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      scaffeine,
      scalaCache,
      javaJwt,
      Play.core,
      Play.ws,
      Play.testPlus,
      Play.akkaTestkit
    )
  )

lazy val `play-auth0-bindings` = (project in file("play-auth0-bindings"))
  .settings(commonSettings)
  .dependsOn(`play-auth0`)
  .settings(
    libraryDependencies ++= Seq(
      guice, // 4.0 to maybe make it work with 2.5 and 2.6
      Play.core
    )
  )

lazy val `play-auth0-root` = (project in file("."))
  .settings(commonSettings)
  .aggregate(`play-auth0`, `play-auth0-bindings`)

mimaDefaultSettings
