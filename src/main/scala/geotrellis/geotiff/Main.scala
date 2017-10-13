package geotrellis.geotiff

import geotrellis.kryo.AvroRegistrator

import akka.actor.Props
import akka.io.IO
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import org.apache.spark.{SparkConf, SparkContext}

object AkkaSystem {
  implicit val system = ActorSystem("geotiff-demo")
  implicit val materializer = ActorMaterializer()

  trait LoggerExecutor {
    protected implicit val log = Logging(system, "app")
  }
}

object Main extends S3COGServiceRouter {
  import AkkaSystem._

  val port = 8777
  val host = "0.0.0.0"

  def main(args: Array[String]): Unit = {
    Http().bindAndHandle(routes, host, port)
  }
}
