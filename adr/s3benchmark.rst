0006 - S3Client benchmark
-------------------------

Context
^^^^^^^

During GeoTiff layer implementation it is required to use S3 range queries to read geotiffs
from S3 (that's how we can query only necessary segments).

P.S. Current COGs status is not optimized, this document describes possible implementation problems and doesn't take into
account the current geotiff layer state with lots of unnecessary data transfer.

Benchmarking
^^^^^^^^^^^^

Test case:

.. code:: scala
    val geoTiffLayer =
      SinglebandGeoTiffCollectionLayerReader
        .fetchSingleband(
          Seq(
            new URI("s3://geotrellis-test/daunnc/LC_TEST/LC08_L1TP_139045_20170304_20170316_01_T1_B4.TIF")
          )
        )

    timedCreate("Read finished") {
      geoTiffLayer.read(LayerId("LC08_L1TP_139045_20170304_20170316_01_T1_B4", 9))(380, 224)
    }

    // ...

    val obj = timedCreate("s3client.getObject(getObjectRequest)")(s3client.getObject(getObjectRequest))
    val stream: S3ObjectInputStream = timedCreate("obj.getObjectContent")(obj.getObjectContent)

    try {
      timedCreate("IOUtils.toByteArray(stream)")(IOUtils.toByteArray(stream))
    } finally stream.close()

    // ...

    def readClippedRange(start: Long, length: Int): Array[Byte] =
      timedCreate("client.readRange(start, start + length, request)") {
        client.readRange(start, start + length, request)
      }

```

Local:
^^^^^^

This case means running the test case on my local machine.
`def readRange` contains

**Initial result (1.2.0 version)**:

* getObjectRequest.setRange(21013289, 21078824): 65536
* s3client.getObject(getObjectRequest) (in 261 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 283 ms)
* client.readRange(start, start + length, request) (in 544 ms)

* getObjectRequest.setRange(21013289, 23375593): 2362305
* s3client.getObject(getObjectRequest) (in 217 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 2,881 ms)
* client.readRange(start, start + length, request) (in 3,098 ms)

* getObjectRequest.setRange(26068606, 26134141): 65536
* s3client.getObject(getObjectRequest) (in 205 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 8 ms)
* client.readRange(start, start + length, request) (in 214 ms)

* getObjectRequest.setRange(26068606, 28468092): 2399487
* s3client.getObject(getObjectRequest) (in 330 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 2,825 ms)
* client.readRange(start, start + length, request) (in 3,156 ms)

* getObjectRequest.setRange(31181945, 31247480): 65536
* s3client.getObject(getObjectRequest) (in 183 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 134 ms)
* client.readRange(start, start + length, request) (in 318 ms)

* getObjectRequest.setRange(31181945, 33446463): 2264519
* s3client.getObject(getObjectRequest) (in 189 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 5,192 ms)
* client.readRange(start, start + length, request) (in 5,381 ms)

* getObjectRequest.setRange(36291738, 36357273): 65536
* s3client.getObject(getObjectRequest) (in 335 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 289 ms)
* client.readRange(start, start + length, request) (in 624 ms)

* getObjectRequest.setRange(36291738, 38161241): 1869504
* s3client.getObject(getObjectRequest) (in 236 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 4,539 ms)
* client.readRange(start, start + length, request) (in 4,775 ms)

* getObjectRequest.setRange(41072048, 41137583): 65536
* s3client.getObject(getObjectRequest) (in 271 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 394 ms)
* client.readRange(start, start + length, request) (in 665 ms)

* getObjectRequest.setRange(41072048, 42836890): 1764843
* s3client.getObject(getObjectRequest) (in 186 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 4,373 ms)
* client.readRange(start, start + length, request) (in 4,559 ms)

* getObjectRequest.setRange(45778226, 45843761): 65536
* s3client.getObject(getObjectRequest) (in 247 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 142 ms)
* client.readRange(start, start + length, request) (in 389 ms)

* getObjectRequest.setRange(45778226, 47330500): 1552275
* s3client.getObject(getObjectRequest) (in 152 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 3,211 ms)
* client.readRange(start, start + length, request) (in 3,363 ms)

EC2 US-West:
^^^^^^^^^^^^

**Initial result (1.2.0 version):**


Range(21013289, 21078824)
+----------------------+-------------------+------------------------------------+
| Function             | Time (ms)         | Comment                            |
+======================+===================+====================================+
| s3client.getObject   | ~261              | Average result                     |
+----------------------+-------------------+------------------------------------+
| toByteArray(stream)  | ~283              | Average result                     |
+----------------------+-------------------+------------------------------------+
| client.readRange     | ~544              | Average result                     |
+----------------------+-------------------+------------------------------------+








+----------------------+-------------------+------------------------------------+
| geoTiffLayer.read    | ~18,541           | ~500mb+ of ram usage to previous   |
+----------------------+-------------------+------------------------------------+

Decision
^^^^^^^^

Conclusion
^^^^^^^^^^

