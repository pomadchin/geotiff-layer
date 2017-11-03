service: geotrellis-geotiff-layer-server

provider:
  name: aws
  runtime: java8
  timeout: 30
  memorySize: 1536
  region: us-west-2
  iamRoleStatements:
    -  Effect: "Allow"
       Action:
         - "s3:*"
       Resource: "arn:aws:s3:::*"
  # stage: dev

# you can define service wide environment variables here
#  environment:
#    variable1: value1

package:
  artifact: lambda/target/scala-2.11/geotiff-layer-lambda-assembly-0.1-SNAPSHOT.jar

functions:
  getTile:
    handler: geotrellis.geotiff.lambda.TileRequestHandler
    events:
      - http:
          path: tile/{z}/{x}/{y}/
          method: get
          response:
            headers:
              Content-Type: "'image/png'"
          integration: lambda
          cors: true
          request:
            template:
              application/json: '{ }'
            parameters:
              paths:
                z: 0
                y: 0
                x: 0
            headers:
              Content-Type: application/json
