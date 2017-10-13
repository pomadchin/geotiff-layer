package geotrellis.chatta

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
  implicit val system = ActorSystem("chatta-demo")
  implicit val materializer = ActorMaterializer()

  trait LoggerExecutor {
    protected implicit val log = Logging(system, "app")
  }
}

object Main extends ChattaS3COGServiceRouter {
  import AkkaSystem._

  val port = 8777
  val host = "0.0.0.0"

  /*lazy val conf = AvroRegistrator(
    new SparkConf()
      .setAppName("GeoTiffLayerDemo")
      .setIfMissing("spark.master", "local[*]")
      .set("spark.serializer", classOf[org.apache.spark.serializer.KryoSerializer].getName)
      .set("spark.kryo.registrator", classOf[geotrellis.spark.io.kryo.KryoRegistrator].getName)
  )

  lazy val sc = new SparkContext(conf)*/

  def main(args: Array[String]): Unit = {
    Http().bindAndHandle(routes, host, port)
  }
}
