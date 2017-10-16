package geotrellis.geotiff

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.s3.geotiff._

import spire.syntax.cfor._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import akka.http.scaladsl.server.Directives

import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import java.net.URI

trait S3COGServiceRouter extends Directives with AkkaSystem.LoggerExecutor {
  def timedCreate3[T](endMsg: String)(f: => T): (String, Double, T) = {
    val s = System.currentTimeMillis
    val result = f
    val e = System.currentTimeMillis
    val t = "%,d".format(e - s)
    val p = s"\t$endMsg (in $t ms)"
    println(p)

    (p, t.replace(",", """.""").toDouble, result)
  }

  def timedCreate[T](endMsg: String)(f: => T): T =
    timedCreate3(endMsg)(f)._3

  // query only rgb metadata and only tiffs
  val filter: String => Boolean =
    s => s.endsWith(".TIF") && (s.contains("B4") || s.contains("B3") || s.contains("B2"))

  // hardcoded paths only for prototype
  val bucketEast = "s3://geotrellis-test/daunnc/LC_TEST"

  val bucket = "s3://landsat-pds/c1"
  val tiles =
    List(
      "L8/139/045/LC08_L1TP_139045_20170304_20170316_01_T1",
      "L8/139/046/LC08_L1TP_139046_20170304_20170316_01_T1"
    )

  val paths =
    tiles.map(p => new URI(s"$bucket/$p"))

  val pathsEast = Seq(new URI(bucketEast))

  def first(band: Int): String = s"LC08_L1TP_139045_20170304_20170316_01_T1_B${band}"
  def second(band: Int): String = s"LC08_L1TP_139046_20170304_20170316_01_T1_B${band}"

  val red1 = first(4)
  val red2 = second(4)

  val green1 = first(3)
  val green2 = second(3)

  val blue1 = first(2)
  val blue2 = second(2)

  val layers = List(red1 -> red2, green1 -> green2, blue1 -> blue2)

  // current metadata is not persistent, it's a sort of a bad in-memory DB.
  println(s"fetching data from s3...")
  val geoTiffLayer = SinglebandGeoTiffCollectionLayerReader.fetchSingleband(paths, filterPaths = filter)
  val geoTiffLayerTest =
    SinglebandGeoTiffCollectionLayerReader
      .fetchSingleband(
        //https://landsat-pds.s3.amazonaws.com/L8/139/045/LC81390452014295LGN00/LC81390452014295LGN00_B4.TIF
        Seq(
          //new URI("s3://geotrellis-test/daunnc/LC_TEST/LC08_L1TP_139045_20170304_20170316_01_T1_B4.TIF")
          new URI(s"s3://landsat-pds/c1/L8/139/045/LC08_L1TP_139045_20170304_20170316_01_T1/LC08_L1TP_139045_20170304_20170316_01_T1_B4.TIF")
        ),
        filterPaths = filter
      )
  println(s"fetching data from s3 finished.")

  val baseZoomLevel = 9

  def layerId(layer: String): LayerId = LayerId(layer, baseZoomLevel)


  def routes = get {
    pathPrefix("gt") {
      pathPrefix("tms")(tms) ~
      pathPrefix("test")(test) ~
      pathPrefix("test")(test2)
    }
  }

  /** Render function to make LC8 beautiful */
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
    (r1, r2) match {
      case (Some(t1), Some(t2)) =>
        t1
          .tile
          .prototype(256, 256)
          .merge(t1)
          .merge(t2)
      case (Some(t1), _) =>
        t1
          .tile
          .prototype(256, 256)
          .merge(t1)
      case (_, Some(t2)) =>
        t2
          .tile
          .prototype(256, 256)
          .merge(t2)
      case _ =>
        IntArrayTile.empty(256, 256)
    }
  }

  def test = pathPrefix("test1") {
    get {
      complete {
        val (str, _, _) = timedCreate3("Read finished") {
          geoTiffLayerTest.read(LayerId("LC08_L1TP_139045_20170304_20170316_01_T1_B4", 9))(380, 224)
        }

        str
      }
    }
  }

  def test2 = pathPrefix("test2") {
    get {
      complete {
        var acc: Double = 0

        cfor(0)(_ < 20, _ + 1) { i =>
          val (_, t, _) = timedCreate3(s"Read finished ($i)") {
            geoTiffLayerTest.read(LayerId("LC08_L1TP_139045_20170304_20170316_01_T1_B4", 9))(380, 224)
          }
          acc += t
        }

        val (str, t, _) = timedCreate3("Read finished (final)") {
          geoTiffLayerTest.read(LayerId("LC08_L1TP_139045_20170304_20170316_01_T1_B4", 9))(380, 224)
        }

        acc += t

        val avgT = acc / 21
        val avgS = s"Average: ${avgT} ms"

        str ++ "\n" ++ avgS
      }
    }
  }

  /** http://localhost:8777/gt/tms/{z}/{x}/{y}/ */
  def tms = pathPrefix(IntNumber / IntNumber / IntNumber) { (zoom, x, y) =>
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
