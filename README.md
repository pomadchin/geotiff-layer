Geotrellis GeoTiff Layer Tile Server
======

## Akka HTTP Server

TODO...

## AWS Lambda

This repository creates a geotrellis tile server on AWS's API Gateway service, backed by AWS Lambda.
The resulting url pattern is:

`https://<aws url>/tile/{z}/{x}/{y}/`

Deployment
------

*tl;dr*: `./scripts/publish`

### Longer version:

You'll need the following AWS permissions:
  - cloudformation:*
  - lambda:*
  - apigateway:*

Once that's set up for your user:

- Configure your AWS profile
- `npm install -g serverless`
- `./scripts/publish`
- Configure your API gateway endpoint from the AWS console:
  - Set binary media types to `image/png` in the options for your API
  - Navigate to the `GET` endpoint and set "Content handling" in its integration response to "Convert to binary (if needed)" for your resource
- Deploy the API using the `Actions` dropdown

Lambda deployment was done basing on the [Lambda Geotrellis Tile Server](https://github.com/azavea/lambda-geotrellis-tile-server) repo.
