lazy val commonSettings = Seq(
  name := "geotiff-layer",
  scalaVersion := "2.11.11",
  organization := "com.azavea",
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-Yinline-warnings",
    "-language:implicitConversions",
    "-language:reflectiveCalls",
    "-language:higherKinds",
    "-language:postfixOps",
    "-language:existentials",
    "-feature"
  ),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  shellPrompt := { s => Project.extract(s).currentProject.id + " > " },
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  test in assembly := {},
  assemblyMergeStrategy in assembly := {
    case "reference.conf" => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case n if n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") => MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case _ => MergeStrategy.first
  },
  initialCommands in console := """
                                  |import io.circe.parser._
                                  |import io.circe.syntax._
                                  |import geotrellis.spark.io._
                                  |import geotrellis.spark.io.s3._
                                """.trim.stripMargin
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(core, lambda, server)

lazy val lambda = project
  .settings(commonSettings: _*)
  .settings(name := "geotiff-layer-lambda")
  .settings(libraryDependencies ++= Seq(
    Dependencies.circeCore,
    Dependencies.circeGeneric,
    Dependencies.circeParser,
    Dependencies.awsJavaCore % Provided,
    Dependencies.awsJavaEvents % Provided,
    Dependencies.awsJavaLog4j % Provided
  ))
  .dependsOn(core)

lazy val server = project
  .settings(commonSettings: _*)
  .settings(name := "geotiff-layer-server")
  .settings(libraryDependencies ++= Seq(
    Dependencies.akkaActor,
    Dependencies.akkaHttpCore,
    Dependencies.akkaHttp,
    Dependencies.akkaHttpSprayJson,
    Dependencies.sparkCore,
    Dependencies.hadoopClient
  ))
  .dependsOn(core)

lazy val core = project
  .settings(commonSettings: _*)
  .settings(name := "geotiff-layer-core")
  .settings(libraryDependencies ++= Seq(
    Dependencies.geotrellisSpark,
    Dependencies.geotrellisS3,
    Dependencies.sparkCore % Provided,
    Dependencies.hadoopClient % Provided
  ))
