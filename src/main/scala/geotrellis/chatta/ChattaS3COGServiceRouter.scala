package geotrellis.chatta

import java.net.URI

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import akka.http.scaladsl.server.Directives
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster._
import geotrellis.raster.mapalgebra.local.Add
import geotrellis.raster.render._
import geotrellis.services._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3.geotiff._
//import geotrellis.spark.io.geotiff._
import geotrellis.vector._
import geotrellis.vector.io.json.Implicits._
import geotrellis.vector.reproject._
import spray.json._

import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ChattaS3COGServiceRouter extends Directives with AkkaSystem.LoggerExecutor with LazyLogging {
  // hardcoded paths only for prototype
  val bucket = "s3://geotrellis-test/daunnc/LC_TEST" //"s3://landsat-pds/c1"
  /*val tiles =
    List(
      "LC08_L1TP_139045_20170304_20170316_01_T1",
      "L8/139/046/LC08_L1TP_139046_20170304_20170316_01_T1"
    )*/
  val paths = Seq(new URI(bucket))
    // tiles.map(p => new URI(s"$bucket/$p"))

  val red1 = "LC08_L1TP_139045_20170304_20170316_01_T1_B4"
  val red2 = "LC08_L1TP_139046_20170304_20170316_01_T1_B4"

  val green1 = "LC08_L1TP_139045_20170304_20170316_01_T1_B3"
  val green2 = "LC08_L1TP_139046_20170304_20170316_01_T1_B3"

  val blue1 = "LC08_L1TP_139045_20170304_20170316_01_T1_B2"
  val blue2 = "LC08_L1TP_139046_20170304_20170316_01_T1_B2"

  println(s"fetching data from s3...")
  val geoTiffLayer = SinglebandGeoTiffCollectionLayerReader.fetchSingleband(paths)
  println(s"fetching data from s3 finished.")

  // val geoTiffLayer: GeoTiffLayer[Tile] = GeoTiffLayer.fromSinglebandFiles(path, ZoomedLayoutScheme(WebMercator))(sc)

  val baseZoomLevel = 9

  def layerId(layer: String): LayerId = LayerId(layer, baseZoomLevel)


  def routes = get {
    pathPrefix("gt") {
      pathPrefix("tms")(tms) ~
      pathPrefix("test")(test)
    }
  }

  def colors = complete(Future(ColorRampMap.getJson))

  /** Render function to make LC8 beatiful */
  def lossyrobRender(r: Tile, g: Tile, b: Tile): MultibandTile = {
    // Landsat
    // magic numbers. Fiddled with until visually it looked ok. ¯\_(ツ)_/¯
    val (min, max) = (4000, 15176)

    def clamp(z: Int) = {
      if (isData(z)) {
        if (z > max) {
          max
        } else if (z < min) {
          min
        } else {
          z
        }
      }
      else {
        z
      }
    }

    val red = r.convert(IntCellType).map(clamp _).normalize(min, max, 0, 255)
    val green = g.convert(IntCellType).map(clamp _).normalize(min, max, 0, 255)
    val blue = b.convert(IntCellType).map(clamp _).normalize(min, max, 0, 255)

    def clampColor(c: Int): Int =
      if (isNoData(c)) {
        c
      }
      else {
        if (c < 0) {
          0
        }
        else if (c > 255) {
          255
        }
        else c
      }

    // -255 to 255
    val brightness = 15

    def brightnessCorrect(v: Int): Int =
      if (v > 0) {
        v + brightness
      }
      else {
        v
      }

    // 0.01 to 7.99
    val gamma = 0.8
    val gammaCorrection = 1 / gamma

    def gammaCorrect(v: Int): Int =
      (255 * math.pow(v / 255.0, gammaCorrection)).toInt

    // -255 to 255
    val contrast: Double = 30.0
    val contrastFactor = (259 * (contrast + 255)) / (255 * (259 - contrast))

    def contrastCorrect(v: Int): Int =
      ((contrastFactor * (v - 128)) + 128).toInt

    def adjust(c: Int): Int = {
      if (isData(c)) {
        var cc = c
        cc = clampColor(brightnessCorrect(cc))
        cc = clampColor(gammaCorrect(cc))
        cc = clampColor(contrastCorrect(cc))
        cc
      } else {
        c
      }
    }

    val adjRed = red.map(adjust _)
    val adjGreen = green.map(adjust _)
    val adjBlue = blue.map(adjust _)

    ArrayMultibandTile(adjRed, adjGreen, adjBlue)
  }

  def merge2tiles(r1: Option[Raster[Tile]], r2: Option[Raster[Tile]]): Tile = {
    //val prototype = IntArrayTile.empty(256, 256)
    (r1, r2) match {
      case (Some(t1), Some(t2)) =>
        println("here0")
        println(s"t1.findMinMax: ${t1.findMinMax}")
        println(s"t2.findMinMax: ${t2.findMinMax}")

        t1
          .tile
          .prototype(256, 256)
          .merge(t1)
          .merge(t2)
      case (Some(t1), _) =>
        println("here1")
        t1
          .tile
          .prototype(256, 256)
          .merge(t1)
      case (_, Some(t2)) =>
        println("here2")
        t2
          .tile
          .prototype(256, 256)
          .merge(t2)
      case _ =>
        println("hereP")
        IntArrayTile.empty(256, 256)
    }
  }

  def test = pathPrefix("test1") {
    get {
      complete {
        val (str, _) = timedCreate2("Read finished") {
          geoTiffLayer.read(LayerId("LC08_L1TP_139045_20170304_20170316_01_T1_B4", 9))(380, 224)
        }

        str
      }
    }
  }

  def tms = pathPrefix(IntNumber / IntNumber / IntNumber) { (zoom, x, y) =>

    val layers = List(red1 -> red2, green1 -> green2, blue1 -> blue2)

    def timedCreate[T](endMsg: String)(f: => T): T = {
      val s = System.currentTimeMillis
      val result = f
      val e = System.currentTimeMillis
      val t = "%,d".format(e - s)
      println(s"\t$endMsg (in $t ms)")
      result
    }

    complete {
      Future {
        println(s"querying $x / $y / $zoom")
        layers.map { case (fst, snd) =>
          for {
            ffst <- Future(Try(timedCreate(s"LayerId($fst, $zoom))($x, $y))")(geoTiffLayer.read(LayerId(fst, zoom))(x, y))).toOption)
            fsnd <- Future(Try(timedCreate(s"LayerId($snd, $zoom))($x, $y))")(geoTiffLayer.read(LayerId(snd, zoom))(x, y))).toOption)
          } yield merge2tiles(ffst, fsnd)
        } match {
          case rf :: gf :: bf :: Nil =>
            val fbytes = for {
              r <- rf
              g <- gf
              b <- bf
            } yield lossyrobRender(r, g, b).renderPng().bytes
            fbytes.map(bytes => HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), bytes)))
          case _ => throw new Exception("Oops smth went wrong")
        }
      }
    }

  }
}
