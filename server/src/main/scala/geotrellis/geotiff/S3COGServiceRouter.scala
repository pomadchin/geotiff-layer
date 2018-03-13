package geotrellis.geotiff

import geotrellis.raster._
import geotrellis.spark._
// import geotrellis.spark.tiling._
// import geotrellis.geotiff.util.CSV
// import geotrellis.spark.io.hadoop.geotiff._
import geotrellis.spark.io.s3.geotiff._
import geotrellis.proj4.WebMercator
import geotrellis.spark.tiling.ZoomedLayoutScheme
import geotrellis.raster.io.geotiff.Auto
import geotrellis.raster.resample._

// import spire.syntax.cfor._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import akka.http.scaladsl.server.Directives
// import jp.ne.opt.chronoscala.Imports._

// import java.net.URI
// import java.time.{LocalDate, ZoneOffset}

// import scala.util.Try
// import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
  import util.Metrics._
  import util.Render._
  import metadata.LC8Metadata._

  // current metadata is not persistent, it's a sort of a bad in-memory DB.
  println(s"fetching data from s3...")
  // val attributeStore: InMemoryGeoTiffAttributeStore = S3IMGeoTiffAttributeStore(() => data)
  // attributeStore.persist(mdJson)
  // mdJson val is from LC8Metadata object
  val attributeStore = S3JsonGeoTiffAttributeStore(mdJson)

  /**
    * Auto(1) setting for LC8 works fine on zoomlevels >= 8
    * Zoom level 7 is not fast enough
    * Probably a better multithreading support can help with it
    *
    * */
  val geoTiffLayer = S3GeoTiffLayerReader(
    attributeStore = attributeStore,
    layoutScheme   = ZoomedLayoutScheme(WebMercator),
    resampleMethod = Bilinear,
    strategy       = Auto(0) // Auto(0) // AutoHigherResolution is the best matching ovr resolution
                             // Auto(1) is a bit better than we need to grab (used this to correspond mapbox qualiy)
  )

  println(s"fetching data from s3 finished.")

  def routes = get {
    pathPrefix("gt") {
      pathPrefix("tms")(tms)
    }
  }

  /** http://localhost:8777/gt/tms/{z}/{x}/{y}/
    * http://52.40.240.211:8777/gt/tms/{z}/{x}/{y}/
    * */
  def tms = pathPrefix(IntNumber / IntNumber / IntNumber) { (zoom, x, y) =>
    complete {
      Future {
        println(s"querying $x / $y / $zoom")
        if(zoom >= 7) {
          val fred: Future[Tile] = Future {
            timedCreate(s"LayerId(RED, $zoom))($x, $y))") {
              try {
                geoTiffLayer.read(LayerId("RED", zoom))(x, y).tile
              } catch {
                case e: Exception =>
                  e.printStackTrace()
                  IntArrayTile.empty(256, 256)
              }
            }
          }

          val fgreen: Future[Tile] = Future {
            timedCreate(s"LayerId(GREEN, $zoom))($x, $y))") {
              try {
                geoTiffLayer.read(LayerId("GREEN", zoom))(x, y).tile
              } catch {
                case e: Exception =>
                  e.printStackTrace()
                  IntArrayTile.empty(256, 256)
              }
            }
          }

          val fblue: Future[Tile] = Future {
            timedCreate(s"LayerId(BLUE, $zoom))($x, $y))") {
              try {
                geoTiffLayer.read(LayerId("BLUE", zoom))(x, y).tile
              } catch {
                case e: Exception =>
                  e.printStackTrace()
                  IntArrayTile.empty(256, 256)
              }
            }
          }

          val fbytes =
            for {
              red <- fred
              green <- fgreen
              blue <- fblue
            } yield lossyrobRender(red, green, blue).renderPng().bytes

          fbytes.map(bytes => HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), bytes)))
        } else {
          Future {
            HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), IntArrayTile.empty(256, 256).renderPng().bytes))
          }
        }
      }
    }
  }
}
