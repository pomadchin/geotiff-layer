package geotrellis.geotiff.lambda.request

import geotrellis.proj4.WebMercator
import geotrellis.raster.io.geotiff.Auto
import geotrellis.raster.render.Png
import geotrellis.raster.resample.Bilinear
import geotrellis.spark.io.s3.geotiff.{S3GeoTiffLayerReader, S3JsonGeoTiffAttributeStore}
import geotrellis.spark.tiling.ZoomedLayoutScheme

import com.amazonaws.services.lambda.runtime.LambdaLogger

abstract class Request {
  val x: Int
  val y: Int
  val z: Int

  def toPng(implicit logger: LambdaLogger): Png
}

object Request {
  import geotrellis.geotiff.metadata.LC8Metadata._

  val attributeStore = S3JsonGeoTiffAttributeStore(mdJson)
  val geoTiffLayer = S3GeoTiffLayerReader(
    attributeStore = attributeStore,
    layoutScheme   = ZoomedLayoutScheme(WebMercator),
    resampleMethod = Bilinear,
    strategy       = Auto(0) // Auto(0) // AutoHigherResolution is the best matching ovr resolution
    // Auto(1) is a bit better than we need to grab (used this to correspond mapbox quality)
  )
}
