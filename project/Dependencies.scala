import sbt._

object Dependencies {
  val geotrellisSpark  = "org.locationtech.geotrellis" %% "geotrellis-spark" % Version.geotrellis
  val geotrellisS3     = "org.locationtech.geotrellis" %% "geotrellis-s3" % Version.geotrellis
  val geotrellisRaster = "org.locationtech.geotrellis" %% "geotrellis-raster" % Version.geotrellis

  val akkaActor         = "com.typesafe.akka" %% "akka-actor" % Version.akkaActor
  val akkaHttpCore      = "com.typesafe.akka" %% "akka-http-core" % Version.akkaHttp
  val akkaHttp          = "com.typesafe.akka" %% "akka-http" % Version.akkaHttp
  val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % Version.akkaHttp

  val sparkCore    = "org.apache.spark"  %% "spark-core"    % Version.sparkCore
  val hadoopClient = "org.apache.hadoop"  % "hadoop-client" % Version.hadoopClient

  val circeCore    = "io.circe" %% "circe-core"           % Version.circe
  val circeGeneric = "io.circe" %% "circe-generic"        % Version.circe
  val circeParser  = "io.circe" %% "circe-parser"         % Version.circe

  val awsJavaCore   = "com.amazonaws" % "aws-lambda-java-core" % Version.awsJavaCore
  val awsJavaEvents = "com.amazonaws" % "aws-lambda-java-events" % Version.awsJavaEvents
  val awsJavaLog4j  = "com.amazonaws" % "aws-lambda-java-log4j" % Version.awsJavaLog4j
}
