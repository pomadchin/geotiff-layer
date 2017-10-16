package geotrellis.geotiff

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

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
