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

getObjectRequest.setRange(21013289, 21078824): 65536

* s3client.getObject(getObjectRequest) (in 261 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 283 ms)
* client.readRange(start, start + length, request) (in 544 ms)

getObjectRequest.setRange(21013289, 23375593): 2362305

* s3client.getObject(getObjectRequest) (in 217 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 2,881 ms)
* client.readRange(start, start + length, request) (in 3,098 ms)

getObjectRequest.setRange(26068606, 26134141): 65536

* s3client.getObject(getObjectRequest) (in 205 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 8 ms)
* client.readRange(start, start + length, request) (in 214 ms)

getObjectRequest.setRange(26068606, 28468092): 2399487

* s3client.getObject(getObjectRequest) (in 330 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 2,825 ms)
* client.readRange(start, start + length, request) (in 3,156 ms)

getObjectRequest.setRange(31181945, 31247480): 65536

* s3client.getObject(getObjectRequest) (in 183 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 134 ms)
* client.readRange(start, start + length, request) (in 318 ms)

getObjectRequest.setRange(31181945, 33446463): 2264519

* s3client.getObject(getObjectRequest) (in 189 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 5,192 ms)
* client.readRange(start, start + length, request) (in 5,381 ms)

getObjectRequest.setRange(36291738, 36357273): 65536

* s3client.getObject(getObjectRequest) (in 335 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 289 ms)
* client.readRange(start, start + length, request) (in 624 ms)

getObjectRequest.setRange(36291738, 38161241): 1869504

* s3client.getObject(getObjectRequest) (in 236 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 4,539 ms)
* client.readRange(start, start + length, request) (in 4,775 ms)

getObjectRequest.setRange(41072048, 41137583): 65536

* s3client.getObject(getObjectRequest) (in 271 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 394 ms)
* client.readRange(start, start + length, request) (in 665 ms)

getObjectRequest.setRange(41072048, 42836890): 1764843

* s3client.getObject(getObjectRequest) (in 186 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 4,373 ms)
* client.readRange(start, start + length, request) (in 4,559 ms)

getObjectRequest.setRange(45778226, 45843761): 65536

* s3client.getObject(getObjectRequest) (in 247 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 142 ms)
* client.readRange(start, start + length, request) (in 389 ms)

getObjectRequest.setRange(45778226, 47330500): 1552275

* s3client.getObject(getObjectRequest) (in 152 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 3,211 ms)
* client.readRange(start, start + length, request) (in 3,363 ms)

**Read finished (in 29,463 ms)**

After warm up:
^^^^^^^^^^^^^^

getObjectRequest.setRange(21013289, 21078824): 65536

* s3client.getObject(getObjectRequest) (in 147 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 412 ms)
* client.readRange(start, start + length, request) (in 559 ms)

getObjectRequest.setRange(21013289, 23375593): 2362305

* s3client.getObject(getObjectRequest) (in 151 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 3,043 ms)
* client.readRange(start, start + length, request) (in 3,194 ms)

getObjectRequest.setRange(26068606, 26134141): 65536

* s3client.getObject(getObjectRequest) (in 249 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 452 ms)
* client.readRange(start, start + length, request) (in 701 ms)

getObjectRequest.setRange(26068606, 28468092): 2399487

* s3client.getObject(getObjectRequest) (in 149 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 3,795 ms)
* client.readRange(start, start + length, request) (in 3,945 ms)

getObjectRequest.setRange(31181945, 31247480): 65536

* s3client.getObject(getObjectRequest) (in 150 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 13 ms)
* client.readRange(start, start + length, request) (in 163 ms)

getObjectRequest.setRange(31181945, 33446463): 2264519

* s3client.getObject(getObjectRequest) (in 147 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 3,993 ms)
* client.readRange(start, start + length, request) (in 4,140 ms)

getObjectRequest.setRange(36291738, 36357273): 65536

* s3client.getObject(getObjectRequest) (in 171 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 23 ms)
* client.readRange(start, start + length, request) (in 194 ms)

getObjectRequest.setRange(36291738, 38161241): 1869504

* s3client.getObject(getObjectRequest) (in 175 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 3,204 ms)
* client.readRange(start, start + length, request) (in 3,380 ms)

getObjectRequest.setRange(41072048, 41137583): 65536

* s3client.getObject(getObjectRequest) (in 155 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 507 ms)
* client.readRange(start, start + length, request) (in 663 ms)

getObjectRequest.setRange(41072048, 42836890): 1764843

* s3client.getObject(getObjectRequest) (in 148 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 3,019 ms)
* client.readRange(start, start + length, request) (in 3,169 ms)

getObjectRequest.setRange(45778226, 45843761): 65536

* s3client.getObject(getObjectRequest) (in 154 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 140 ms)
* client.readRange(start, start + length, request) (in 294 ms)

getObjectRequest.setRange(45778226, 47330500): 1552275

* s3client.getObject(getObjectRequest) (in 149 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 2,131 ms)
* client.readRange(start, start + length, request) (in 2,281 ms)

**Read finished (final)**: 23,516 ms
**Average, 21 iterations**: 37,564 ms

**Futures parallelized version with ranges no longer than 10k bytes**:  ¯\_(ツ)_/¯

Logs are skipped as it's huge.

**Read finished (final)**: 17,232 ms
**Average, 21 iterations**: 16,740 ms

EC2 US-West (but data accidentally is on US-East):
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**Initial result (1.2.0 version):**

getObjectRequest.setRange(21013289, 21078824): 65536

* s3client.getObject(getObjectRequest) (in 157 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 245 ms)
* client.readRange(start, start + length, request) (in 402 ms)

getObjectRequest.setRange(21013289, 23375593): 2362305

* s3client.getObject(getObjectRequest) (in 136 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 2,617 ms)
* client.readRange(start, start + length, request) (in 2,754 ms)

getObjectRequest.setRange(26068606, 26134141): 65536

* s3client.getObject(getObjectRequest) (in 185 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 81 ms)
* client.readRange(start, start + length, request) (in 267 ms)

getObjectRequest.setRange(26068606, 28468092): 2399487

* s3client.getObject(getObjectRequest) (in 152 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 1,712 ms)
* client.readRange(start, start + length, request) (in 1,865 ms)

getObjectRequest.setRange(31181945, 31247480): 65536

* s3client.getObject(getObjectRequest) (in 167 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 4 ms)
* client.readRange(start, start + length, request) (in 171 ms)

getObjectRequest.setRange(31181945, 33446463): 2264519

* s3client.getObject(getObjectRequest) (in 141 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 914 ms)
* client.readRange(start, start + length, request) (in 1,055 ms)

getObjectRequest.setRange(36291738, 36357273): 65536

* s3client.getObject(getObjectRequest) (in 229 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 4 ms)
* client.readRange(start, start + length, request) (in 233 ms)

getObjectRequest.setRange(36291738, 38161241): 1869504

* s3client.getObject(getObjectRequest) (in 186 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 658 ms)
* client.readRange(start, start + length, request) (in 844 ms)

getObjectRequest.setRange(41072048, 41137583): 65536

* s3client.getObject(getObjectRequest) (in 165 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 4 ms)
* client.readRange(start, start + length, request) (in 170 ms)

getObjectRequest.setRange(41072048, 42836890): 1764843

* s3client.getObject(getObjectRequest) (in 264 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 497 ms)
* client.readRange(start, start + length, request) (in 762 ms)

getObjectRequest.setRange(45778226, 45843761): 65536

* s3client.getObject(getObjectRequest) (in 108 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 4 ms)
* client.readRange(start, start + length, request) (in 112 ms)

getObjectRequest.setRange(45778226, 47330500): 1552275

* s3client.getObject(getObjectRequest) (in 116 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 341 ms)
* client.readRange(start, start + length, request) (in 457 ms)

**Read finished:** 10,078 ms

After warm up:
^^^^^^^^^^^^^^

getObjectRequest.setRange(21013289, 21078824): 65536

* s3client.getObject(getObjectRequest) (in 89 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 223 ms)
* client.readRange(start, start + length, request) (in 312 ms)

getObjectRequest.setRange(21013289, 23375593): 2362305

* s3client.getObject(getObjectRequest) (in 88 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 2,298 ms)
* client.readRange(start, start + length, request) (in 2,386 ms)

getObjectRequest.setRange(26068606, 26134141): 65536

* s3client.getObject(getObjectRequest) (in 88 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 4 ms)
* client.readRange(start, start + length, request) (in 92 ms)

getObjectRequest.setRange(26068606, 28468092): 2399487

* s3client.getObject(getObjectRequest) (in 91 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 1,119 ms)
* client.readRange(start, start + length, request) (in 1,211 ms)

getObjectRequest.setRange(31181945, 31247480): 65536

* s3client.getObject(getObjectRequest) (in 87 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 4 ms)
* client.readRange(start, start + length, request) (in 91 ms)

getObjectRequest.setRange(31181945, 33446463): 2264519

* s3client.getObject(getObjectRequest) (in 88 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 749 ms)
* client.readRange(start, start + length, request) (in 837 ms)

getObjectRequest.setRange(36291738, 36357273): 65536

* s3client.getObject(getObjectRequest) (in 157 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 5 ms)
* client.readRange(start, start + length, request) (in 162 ms)

getObjectRequest.setRange(36291738, 38161241): 1869504

* s3client.getObject(getObjectRequest) (in 94 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 529 ms)
* client.readRange(start, start + length, request) (in 623 ms)

getObjectRequest.setRange(41072048, 41137583): 65536

* s3client.getObject(getObjectRequest) (in 97 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 3 ms)
* client.readRange(start, start + length, request) (in 102 ms)

getObjectRequest.setRange(41072048, 42836890): 1764843

* s3client.getObject(getObjectRequest) (in 87 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 449 ms)
* client.readRange(start, start + length, request) (in 537 ms)

getObjectRequest.setRange(45778226, 45843761): 65536

* s3client.getObject(getObjectRequest) (in 88 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 4 ms)
* client.readRange(start, start + length, request) (in 92 ms)

getObjectRequest.setRange(45778226, 47330500): 1552275

* s3client.getObject(getObjectRequest) (in 89 ms)
* obj.getObjectContent (in 0 ms)
* IOUtils.toByteArray(stream) (in 319 ms)
* client.readRange(start, start + length, request) (in 408 ms)


**Read finished (final)**: 7,470 ms
**Average, 21 iterations**: 4,910 ms

with a better stream func
**Read finished (final) (in 3,295 ms)**
**Average: 3.942476190476189 ms**

**Futures parallelized version with ranges no longer than 10k bytes**:  ¯\_(ツ)_/¯

Logs are skipped as it's huge.

**Read finished (final)**: 5,458 ms
**Average, 21 iterations**: 5,04 ms

Decision
^^^^^^^^

The only change was to use

  .. code:: scala


    sun.misc.IOUtils.readFully
    // instead of
    IOUtils.toByteArray


Conclusion
^^^^^^^^^^

It makes sense to parallelize connections on machines with a slow connection. If machines are both in the same region it
doesn't make any sense and even can cause slowdowns. The poor results above show how slow data access is from a `west-1`
machine to data on `east-1`. S3 works very good even as is, and the motivation to write this ADR was a typo in links to
example source data.

When data is located with server in the same region the result is 1s average per tile.
For sure it works slower on higher zoom levels but for higher zoom levels we can use overviews.

In addition it looks like fetching tiff tags takes 10-20ms from 1 second query, and probably it a final
micro optimisation and makes sense only after making the current API more stable.