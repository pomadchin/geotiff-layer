package geotrellis.geotiff.util

object Metrics {
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
}
