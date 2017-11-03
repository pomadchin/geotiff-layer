package geotrellis.geotiff.lambda

import geotrellis.geotiff.lambda.request._

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe.Json
import io.circe.parser.{parse, decode}

import java.io.{InputStream, OutputStream}
import java.util.Base64

class TileRequestHandler extends RequestStreamHandler {
  def handleRequest(input: InputStream, out: OutputStream, context: Context): Unit = {
    val encoder = Base64.getEncoder()
    val payload = scala.io.Source.fromInputStream(input).mkString("")
    val payloadJson: Json = parse(payload) match {
      case Right(js) => js
      case Left(e) => throw e
    }
    implicit val logger = context.getLogger()
    logger.log(s"$payload")
    val decoded = decode[DefaultRequest](payload)
    val encodedBytes = decoded match {
      case Right(req) =>
        logger.log(s"Decoded class was ${req.getClass.toString}")
        encoder.encode {
          if (req.z >= 7) req.toPng.bytes
          else EmptyRequest(req.x, req.y, req.z).toPng.bytes
        }
      case Left(_) =>
        throw new Exception(payloadJson.spaces2)
    }
    out.write(encodedBytes)
  }
}
