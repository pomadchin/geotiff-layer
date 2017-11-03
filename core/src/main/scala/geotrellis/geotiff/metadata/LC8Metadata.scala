package geotrellis.geotiff.metadata

import geotrellis.geotiff.util.CSV
import geotrellis.spark.io.hadoop.geotiff.GeoTiffMetadata
import geotrellis.spark.io.s3.geotiff.S3GeoTiffInput

import jp.ne.opt.chronoscala.Imports._
import java.net.URI
import java.time.{LocalDate, ZoneOffset}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scala.util.control.Breaks.{break, breakable}

object LC8Metadata {
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

  /*
  // Inaccurate full LC8 coverage
  val listBuffer: ListBuffer[Future[List[GeoTiffMetadata]]] = ListBuffer()
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
}
