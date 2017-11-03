package geotrellis.geotiff.util

import com.opencsv._
import java.io._
import java.net._

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.s3.{AmazonS3URI, AmazonS3Client => AWSAmazonS3Client}

object CSV {
  /** Convert URIs into input streams, branching based on URI type */
  def getStream(uri: URI): InputStream = uri.getScheme match {
    case "file" =>
      new FileInputStream(new File(uri))
    case "http" =>
      uri.toURL.openStream
    case "https" =>
      uri.toURL.openStream
    case "s3" =>
      val client = new AWSAmazonS3Client(new DefaultAWSCredentialsProviderChain)
      val s3uri = new AmazonS3URI(uri)
      client.getObject(s3uri.getBucket, s3uri.getKey).getObjectContent

    case _ =>
      throw new IllegalArgumentException(s"Resource at $uri is not valid")
  }

  def getBiFunctions(header: Array[String]): (Map[String, Int], Map[Int, String]) = {
    val idToIndex: Map[String, Int] = header.zipWithIndex.toMap
    val indexToId = idToIndex map { case (v, i) => i -> v }

    idToIndex -> indexToId
  }

  def parse(uri: URI): CSVReader = new CSVReader(new InputStreamReader(getStream(uri)))

  def parse(uri: String): CSVReader = parse(new URI(uri))
}