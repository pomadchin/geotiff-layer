package geotrellis.geotiff

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.spark.io.hadoop.geotiff._
import geotrellis.spark.io.s3.geotiff._
import geotrellis.proj4.WebMercator
import geotrellis.spark.io.hadoop.geotiff.InMemoryGeoTiffAttributeStore
import geotrellis.spark.io.s3.S3Client
import geotrellis.spark.tiling.ZoomedLayoutScheme
import spire.syntax.cfor._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import akka.http.scaladsl.server.Directives
import jp.ne.opt.chronoscala.Imports._
import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.model.ObjectMetadata

import scala.util.Try
import java.net.URI
import java.time.{LocalDate, ZoneOffset}

import geotrellis.geotiff.util.CSV

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.control.Breaks._

/**
  *
  * Create a concurrent S3RangeReader issue (weird colored tiles on geotiff layer demo tms url)
  *
  * GeoTiff Layer
  *
  * Bands descriminations, tuples of bands / layers? o:
  *
  * To factor out the logic to handle files selection into a separate API // WTF? O:
  * Mb it would be an interface, and we'll have only a > default implementation <
  * Very minimal / couple levels of deep
  *
  * Mb a builder, to describe how geotiff layer _scheme_ should look like?
  *
  * Metadata ^^ //>
  *
  * serialize database on s3 into json for instance
  *   index // sfc -> path
  *
  * Metadata store should be a generic interface which can be extended by RF / PSQL to keep indexed tiffs metadata
  *
  * Option[TiffTags] <--
  *  projection
  *  nodatavalues
  *
  *
  *  Rename Tiffs according to geohash (!)
  *
  * <------------------------------------->
  *
  * Instrumentation to benchmark metadata
  *
  * The segment lookup
  *
  * TiffTags => Segments function
  *
  * It would be time wich _can_ be removed by sfc <!!>
  *
  * And segment Layout fetchin would be a total bullshit
  *
  * <------------------------------------>
  *
  * COGLayer
  *
  * Index segments
  *
  *
  * */

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

  // current metadata is not persistent, it's a sort of a bad in-memory DB.
  println(s"fetching data from s3...")

  val mdJson = new URI("s3://geotrellis-test/daunnc/geotiff-layer/metadata.json")

  val bucket = "s3://landsat-pds/c1"

  def getLandsatPath(productId: String)(b: Int): (URI, String) = {
    val p = productId.split("_")(2)
    val (wPath, wRow) = p.substring(0, 3) -> p.substring(3, 6)
    val rpath = s"${productId}_B${b}.TIF"
    val path = s"${bucket}/L8/$wPath/$wRow/$productId/${rpath}"
    //println(s"Constructed path: $path -> $rpath")
    new URI(path) -> rpath
  }

  /** Inaccurate full LC8 coverage */
  //val lcpaths = 0 to 250 map { i => f"${i}%03d" } toList
  //val lcrows = 0 to 250 map { i => f"${i}%03d" } toList


  def collectMetadata = {
    var counter = 0
    val threshold = 10

    val md: String = "https://landsat.usgs.gov/landsat/metadata_service/bulk_metadata_files/LANDSAT_8_C1.csv"

    val startDate: LocalDate = LocalDate.of(2017, 10, 1)
    val endDate: LocalDate = LocalDate.now(ZoneOffset.UTC)

    val reader = CSV.parse(md)
    val iterator = reader.iterator()

    val listBuffer = ListBuffer[Future[List[GeoTiffMetadata]]]()
    val (_, indexToId) = CSV.getBiFunctions(iterator.next())

    breakable {
      while (iterator.hasNext) {
        Try {
          val line = reader.readNext()

          val row =
            line
              .zipWithIndex
              .map { case (v, i) => indexToId(i) -> v }
              .toMap


          row.get("acquisitionDate") foreach { dateStr =>
            val date = LocalDate.parse(dateStr)
            if (startDate <= date && endDate > date) {
              val productId = row("LANDSAT_PRODUCT_ID")
              if (productId.contains("T1")) {
                val (red, nred) = getLandsatPath(productId)(4)
                val (green, ngreen) = getLandsatPath(productId)(3)
                val (blue, nblue) = getLandsatPath(productId)(2)

                val fred =
                  Future {
                    println(s"fetching red $nred...")
                    S3GeoTiffInput.list(
                      "RED",
                      red,
                      s"(.)*${nred}",
                      true
                    )
                  }

                val fgreen =
                  Future {
                    println(s"fetching green $ngreen...")
                    S3GeoTiffInput.list(
                      "GREEN",
                      green,
                      s"(.)*${ngreen}",
                      true
                    )
                  }

                val fblue =
                  Future {
                    println(s"fetching blue $nblue...")
                    S3GeoTiffInput.list(
                      "BLUE",
                      blue,
                      s"(.)*${nblue}",
                      true
                    )
                  }

                val result: Future[List[GeoTiffMetadata]] =
                  for {
                    red <- fred
                    green <- fgreen
                    blue <- fblue
                  } yield red ::: green ::: blue

                listBuffer += result
              }
            } else if (date < startDate) {
              counter += 1
            }
          }

          if (counter > threshold) break
        }
      }
    }

    listBuffer
  }

  lazy val listBuffer = collectMetadata




  /*val listBuffer: ListBuffer[Future[List[GeoTiffMetadata]]] = ListBuffer()
  println(s"fetching metadata...")
  cfor(0)(_ < 250, _ + 1) { i =>
    cfor(0)(_ < 250, _ + 1) { j =>
      println(s"fetching path / row: $i / $j")

      val lcpath = f"${i}%03d"
      val lcrow = f"${j}%03d"

      val fred =
        Future {
          println(s"fetching red ($lcpath / $lcrow)...")
          S3GeoTiffInput.list(
            "RED",
            new URI(s"$bucket/L8/$lcpath/$lcrow"),
            s"(.)*LC08_L1TP_${lcpath}${lcrow}_2017(.)*_2017(.)*_01_T1_B4.TIF",
            true
          )
        }

      val fgreen =
        Future {
          println(s"fetching green ($lcpath / $lcrow)...")
          S3GeoTiffInput.list(
            "GREEN",
            new URI(s"$bucket/L8/$lcpath/$lcrow"),
            s"(.)*LC08_L1TP_${lcpath}${lcrow}_2017(.)*_2017(.)*_01_T1_B3.TIF",
            true
          )
        }

      val fblue =
        Future {
          println(s"fetching blue ($lcpath / $lcrow)...")
          S3GeoTiffInput.list(
            "BLUE",
            new URI(s"$bucket/L8/$lcpath/$lcrow"),
            s"(.)*LC08_L1TP_${lcpath}${lcrow}_2017(.)*_2017(.)*_01_T1_B2.TIF",
            true
          )
        }

      val result: Future[List[GeoTiffMetadata]] =
        for {
          red <- fred
          green <- fgreen
          blue <- fblue
        } yield red ::: green ::: blue

      listBuffer += result
    }
  }*/

  lazy val fdata: List[Future[List[GeoTiffMetadata]]] = listBuffer.toList

  lazy val data: List[GeoTiffMetadata] = Await.result(Future.sequence(fdata).map {
    _.flatten
  }, Duration.Inf)

  //val attributeStore: InMemoryGeoTiffAttributeStore = S3IMGeoTiffAttributeStore(() => data)
  val attributeStore = S3JsonGeoTiffAttributeStore(mdJson)

  //attributeStore.persist(mdJson)

  val geoTiffLayer = S3GeoTiffLayerReader(attributeStore, ZoomedLayoutScheme(WebMercator))
  println(s"fetching data from s3 finished.")

  val baseZoomLevel = 9

  def layerId(layer: String): LayerId = LayerId(layer, baseZoomLevel)


  def routes = get {
    pathPrefix("gt") {
      pathPrefix("tms")(tms)
      /*~
      pathPrefix("test")(test) ~
      pathPrefix("test")(test2)*/
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

  /*def test = pathPrefix("test1") {
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
  }*/

  /** http://localhost:8777/gt/tms/{z}/{x}/{y}/
    * http://52.43.9.31:8777/gt/tms/{z}/{x}/{y}/
    * */
  def tms = pathPrefix(IntNumber / IntNumber / IntNumber) { (zoom, x, y) =>
    complete {
      Future {
        println(s"querying $x / $y / $zoom")
        val fred: Future[Tile] = Future {
          timedCreate(s"LayerId(RED, $zoom))($x, $y))") {
            Try(geoTiffLayer.read(LayerId("RED", zoom))(x, y).tile)
              .toOption
              .getOrElse(IntArrayTile.empty(256, 256))
          }
        }

        val fgreen: Future[Tile] = Future {
          timedCreate(s"LayerId(GREEN, $zoom))($x, $y))") {
            Try(geoTiffLayer.read(LayerId("GREEN", zoom))(x, y).tile)
              .toOption
              .getOrElse(IntArrayTile.empty(256, 256))
          }
        }

        val fblue: Future[Tile] = Future {
          timedCreate(s"LayerId(BLUE, $zoom))($x, $y))") {
            Try(geoTiffLayer.read(LayerId("BLUE", zoom))(x, y).tile)
              .toOption
              .getOrElse(IntArrayTile.empty(256, 256))
          }
        }

        val fbytes =
          for {
            red <- fred
            green <- fgreen
            blue <- fblue
          } yield lossyrobRender(red, green, blue).renderPng().bytes

        fbytes.map(bytes => HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), bytes)))
      }
    }

  }
}
