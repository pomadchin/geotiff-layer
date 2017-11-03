package geotrellis.geotiff.util

import geotrellis.raster._

object Render {
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
}
