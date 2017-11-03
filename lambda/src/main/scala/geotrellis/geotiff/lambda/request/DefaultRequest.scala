package geotrellis.geotiff.lambda.request

import geotrellis.raster.{IntArrayTile, Tile}
import geotrellis.raster.render._
import geotrellis.spark.LayerId

import com.amazonaws.services.lambda.runtime.LambdaLogger
import io.circe.generic.JsonCodec

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

@JsonCodec
case class DefaultRequest (
  x: Int,
  y: Int,
  z: Int
) extends Request {
  import geotrellis.geotiff.util.Metrics._
  import geotrellis.geotiff.util.Render._
  import Request._

  def toPng(implicit logger: LambdaLogger): Png = {
    Await.result((Future {
      println(s"querying $x / $y / $z")
      if(z >= 7) {
        val fred: Future[Tile] = Future {
          timedCreate(s"LayerId(RED, $z))($x, $y))") {
            try {
              geoTiffLayer.read(LayerId("RED", z))(x, y).tile
            } catch {
              case e: Exception =>
                e.printStackTrace()
                IntArrayTile.empty(256, 256)
            }
          }
        }

        val fgreen: Future[Tile] = Future {
          timedCreate(s"LayerId(GREEN, $z))($x, $y))") {
            try {
              geoTiffLayer.read(LayerId("GREEN", z))(x, y).tile
            } catch {
              case e: Exception =>
                e.printStackTrace()
                IntArrayTile.empty(256, 256)
            }
          }
        }

        val fblue: Future[Tile] = Future {
          timedCreate(s"LayerId(BLUE, $z))($x, $y))") {
            try {
              geoTiffLayer.read(LayerId("BLUE", z))(x, y).tile
            } catch {
              case e: Exception =>
                e.printStackTrace()
                IntArrayTile.empty(256, 256)
            }
          }
        }

        val fpng =
          for {
            red <- fred
            green <- fgreen
            blue <- fblue
          } yield lossyrobRender(red, green, blue).renderPng()
        fpng
      } else {
        Future { IntArrayTile.empty(256, 256).renderPng() }
      }
    }).flatMap(identity), Duration.Inf)
  }
}

@JsonCodec
case class EmptyRequest (
  x: Int,
  y: Int,
  z: Int
) extends Request {
  def toPng(implicit logger: LambdaLogger): Png = IntArrayTile.empty(256, 256).renderPng()
}
